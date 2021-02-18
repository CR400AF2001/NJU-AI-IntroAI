# Copyright 2019 DeepMind Technologies Ltd. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""DQN agent implemented in TensorFlow."""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import collections
import random, os
import numpy as np
import sonnet as snt
import tensorflow as tf

Transition = collections.namedtuple(
    "Transition",
    "info_state action reward next_info_state is_final_step legal_actions_mask")

StepOutput = collections.namedtuple("step_output", ["action", "probs"])

ILLEGAL_ACTION_LOGITS_PENALTY = -1e9


class ReplayBuffer(object):
    """ReplayBuffer of fixed size with a FIFO replacement policy.

    Stored transitions can be sampled uniformly.

    The underlying datastructure is a ring buffer, allowing 0(1) adding and
    sampling.
    """

    def __init__(self, replay_buffer_capacity):
        self._replay_buffer_capacity = replay_buffer_capacity
        self._data = []
        self._next_entry_index = 0

    def add(self, element):
        """Adds `element` to the buffer.

        If the buffer is full, the oldest element will be replaced.

        Args:
          element: data to be added to the buffer.
        """
        if len(self._data) < self._replay_buffer_capacity:
            self._data.append(element)
        else:
            self._data[self._next_entry_index] = element
            self._next_entry_index += 1
            self._next_entry_index %= self._replay_buffer_capacity

    def sample(self, num_samples):
        """Returns `num_samples` uniformly sampled from the buffer.

        Args:
          num_samples: `int`, number of samples to draw.

        Returns:
          An iterable over `num_samples` random elements of the buffer.

        Raises:
          ValueError: If there are less than `num_samples` elements in the buffer
        """
        if len(self._data) < num_samples:
            raise ValueError("{} elements could not be sampled from size {}".format(
                num_samples, len(self._data)))
        return random.sample(self._data, num_samples)

    def __len__(self):
        return len(self._data)

    def __iter__(self):
        return iter(self._data)


class DQN:
    """DQN Agent implementation in TensorFlow.
    """

    def __init__(self,
                 session,
                 player_id,
                 state_representation_size,
                 num_actions,
                 hidden_layers_sizes,
                 replay_buffer_capacity=10000,
                 batch_size=128,
                 replay_buffer_class=ReplayBuffer,
                 learning_rate=0.01,
                 update_target_network_every=200,
                 learn_every=10,
                 discount_factor=1.0,
                 min_buffer_size_to_learn=1000,
                 epsilon_start=1.0,
                 epsilon_end=0.1,
                 epsilon_decay_duration=int(1e6),
                 optimizer_str="sgd",
                 loss_str="mse",
                 max_global_gradient_norm=None):
        """Initialize the DQN agent."""
        self.player_id = player_id
        self._session = session
        self._num_actions = num_actions
        self._layer_sizes = hidden_layers_sizes + [num_actions]
        self._batch_size = batch_size
        self._update_target_network_every = update_target_network_every
        self._learn_every = learn_every
        self._min_buffer_size_to_learn = min_buffer_size_to_learn
        self._discount_factor = discount_factor

        self._epsilon_start = epsilon_start
        self._epsilon_end = epsilon_end
        self._epsilon_decay_duration = epsilon_decay_duration

        # TODO Allow for optional replay buffer config.
        self._replay_buffer = replay_buffer_class(replay_buffer_capacity)
        self._prev_timestep = None
        self._prev_action = None

        # Step counter to keep track of learning, eps decay and target network.
        self._step_counter = 0

        # Keep track of the last training loss achieved in an update step.
        self._last_loss_value = None

        # Create required TensorFlow placeholders to perform the Q-network updates.
        self._info_state_ph = tf.placeholder(
            shape=[None, state_representation_size],
            dtype=tf.float32,
            name="info_state_ph")
        self._action_ph = tf.placeholder(
            shape=[None], dtype=tf.int32, name="action_ph")
        self._reward_ph = tf.placeholder(
            shape=[None], dtype=tf.float32, name="reward_ph")
        self._is_final_step_ph = tf.placeholder(
            shape=[None], dtype=tf.float32, name="is_final_step_ph")
        self._next_info_state_ph = tf.placeholder(
            shape=[None, state_representation_size],
            dtype=tf.float32,
            name="next_info_state_ph")
        self._legal_actions_mask_ph = tf.placeholder(
            shape=[None, num_actions],
            dtype=tf.float32,
            name="legal_actions_mask_ph")

        self._q_network = snt.nets.MLP(output_sizes=self._layer_sizes)
        self._q_values = self._q_network(self._info_state_ph)
        self._target_q_network = snt.nets.MLP(output_sizes=self._layer_sizes)
        self._target_q_values = self._target_q_network(self._next_info_state_ph)

        # Stop gradient to prevent updates to the target network while learning
        self._target_q_values = tf.stop_gradient(self._target_q_values)

        self._update_target_network = self._create_target_network_update_op(
            self._q_network, self._target_q_network)

        # Create the loss operations.
        # Sum a large negative constant to illegal action logits before taking the
        # max. This prevents illegal action values from being considered as target.
        illegal_actions = 1 - self._legal_actions_mask_ph
        illegal_logits = illegal_actions * ILLEGAL_ACTION_LOGITS_PENALTY
        max_next_q = tf.reduce_max(
            tf.math.add(tf.stop_gradient(self._target_q_values), illegal_logits),
            axis=-1)
        target = (
                self._reward_ph +
                (1 - self._is_final_step_ph) * self._discount_factor * max_next_q)

        action_indices = tf.stack(
            [tf.range(tf.shape(self._q_values)[0]), self._action_ph], axis=-1)
        predictions = tf.gather_nd(self._q_values, action_indices)

        if loss_str == "mse":
            loss_class = tf.losses.mean_squared_error
        elif loss_str == "huber":
            loss_class = tf.losses.huber_loss
        else:
            raise ValueError("Not implemented, choose from 'mse', 'huber'.")

        self._loss = tf.reduce_mean(
            loss_class(labels=target, predictions=predictions))

        if optimizer_str == "adam":
            optimizer = tf.train.AdamOptimizer(learning_rate=learning_rate)
        elif optimizer_str == "sgd":
            optimizer = tf.train.GradientDescentOptimizer(learning_rate=learning_rate)
        else:
            raise ValueError("Not implemented, choose from 'adam' and 'sgd'.")

        def minimize_with_clipping(optimizer, loss):
            grads_and_vars = optimizer.compute_gradients(loss)
            if max_global_gradient_norm is not None:
                grads, variables = zip(*grads_and_vars)
                grads, _ = tf.clip_by_global_norm(grads, max_global_gradient_norm)
                grads_and_vars = list(zip(grads, variables))

            return optimizer.apply_gradients(grads_and_vars)

        self._learn_step = minimize_with_clipping(optimizer, self._loss)

        # self._ckp = tf.train.Checkpoint(module=self._q_network)
        self._saver = tf.train.Saver(var_list=self._q_network.variables)

    def step(self, time_step, is_evaluation=False, add_transition_record=True):
        """Returns the action to be taken and updates the Q-network if needed.

        Args:
          time_step: an instance of TimeStep
          is_evaluation: bool, whether this is a training or evaluation call.
          add_transition_record: Whether to add to the replay buffer on this step.

        Returns:
          A `StepOutput` containing the action probs and chosen action.
        """
        # Act step: don't act at terminal info states or if its not our turn.
        if (not time_step.last()) and (self.player_id == time_step.current_player()):
            info_state = time_step.observations["info_state"][self.player_id]
            legal_actions = time_step.observations["legal_actions"][self.player_id]
            epsilon = self._get_epsilon(is_evaluation)
            action, probs = self._epsilon_greedy(info_state, legal_actions, epsilon)
        else:
            action = None
            probs = []

        # Don't mess up with the state during evaluation.
        if not is_evaluation:
            self._step_counter += 1

            if self._step_counter % self._learn_every == 0:
                self._last_loss_value = self.learn()

            if self._step_counter % self._update_target_network_every == 0:
                self._session.run(self._update_target_network)

            if self._prev_timestep and add_transition_record:
                # We may omit record adding here if it's done elsewhere.
                if self._prev_action is not None:
                    self.add_transition(self._prev_timestep, self._prev_action, time_step)

            if time_step.last():  # prepare for the next episode.
                self._prev_timestep = None
                self._prev_action = None
                return
            else:
                self._prev_timestep = time_step
                self._prev_action = action

        return StepOutput(action=action, probs=probs)

    def add_transition(self, prev_time_step, prev_action, time_step):
        """Adds the new transition using `time_step` to the replay buffer.

        Adds the transition from `self._prev_timestep` to `time_step` by
        `self._prev_action`.

        Args:
          prev_time_step: prev ts, an instance of rl_environment.TimeStep.
          prev_action: int, action taken at `prev_time_step`.
          time_step: current ts, an instance of rl_environment.TimeStep.
        """
        assert prev_time_step is not None
        legal_actions = (
            prev_time_step.observations["legal_actions"][self.player_id])
        legal_actions_mask = np.zeros(self._num_actions)
        legal_actions_mask[legal_actions] = 1.0
        transition = Transition(
            info_state=(
                prev_time_step.observations["info_state"][self.player_id][:]),
            action=prev_action,
            reward=time_step.rewards[self.player_id],
            next_info_state=time_step.observations["info_state"][self.player_id][:],
            is_final_step=float(time_step.last()),
            legal_actions_mask=legal_actions_mask)
        self._replay_buffer.add(transition)

    def _create_target_network_update_op(self, q_network, target_q_network):
        """Create TF ops copying the params of the Q-network to the target network.

        Args:
          q_network: `snt.AbstractModule`. Values are copied from this network.
          target_q_network: `snt.AbstractModule`. Values are copied to this network.

        Returns:
          A `tf.Operation` that updates the variables of the target.
        """
        variables = q_network.get_variables()
        target_variables = target_q_network.get_variables()
        return tf.group([
            tf.assign(target_v, v)
            for (target_v, v) in zip(target_variables, variables)
        ])

    def _epsilon_greedy(self, info_state, legal_actions, epsilon):
        """Returns a valid epsilon-greedy action and valid action probs.

        Action probabilities are given by a softmax over legal q-values.

        Args:
          info_state: hashable representation of the information state.
          legal_actions: list of legal actions at `info_state`.
          epsilon: float, probability of taking an exploratory action.

        Returns:
          A valid epsilon-greedy action and valid action probabilities.
        """
        probs = np.zeros(self._num_actions)
        if np.random.rand() < epsilon:
            action = np.random.choice(legal_actions)
            probs[legal_actions] = 1.0 / len(legal_actions)
        else:
            info_state = np.reshape(info_state, [1, -1])
            q_values = self._session.run(
                self._q_values, feed_dict={self._info_state_ph: info_state})[0]
            legal_q_values = q_values[legal_actions]
            action = legal_actions[np.argmax(legal_q_values)]
            probs[action] = 1.0
        return action, probs

    def _get_epsilon(self, is_evaluation, power=1.0):
        """Returns the evaluation or decayed epsilon value."""
        if is_evaluation:
            return 0.0
        decay_steps = min(self._step_counter, self._epsilon_decay_duration)
        decayed_epsilon = (
                self._epsilon_end + (self._epsilon_start - self._epsilon_end) *
                (1 - decay_steps / self._epsilon_decay_duration) ** power)
        return decayed_epsilon

    def learn(self):
        """Compute the loss on sampled transitions and perform a Q-network update.

        If there are not enough elements in the buffer, no loss is computed and
        `None` is returned instead.

        Returns:
          The average loss obtained on this batch of transitions or `None`.
        """

        if (len(self._replay_buffer) < self._batch_size or
                len(self._replay_buffer) < self._min_buffer_size_to_learn):
            return None

        transitions = self._replay_buffer.sample(self._batch_size)
        info_states = [t.info_state for t in transitions]
        actions = [t.action for t in transitions]
        rewards = [t.reward for t in transitions]
        next_info_states = [t.next_info_state for t in transitions]
        are_final_steps = [t.is_final_step for t in transitions]
        legal_actions_mask = [t.legal_actions_mask for t in transitions]
        loss, _ = self._session.run(
            [self._loss, self._learn_step],
            feed_dict={
                self._info_state_ph: info_states,
                self._action_ph: actions,
                self._reward_ph: rewards,
                self._is_final_step_ph: are_final_steps,
                self._next_info_state_ph: next_info_states,
                self._legal_actions_mask_ph: legal_actions_mask,
            })
        return loss

    def save(self, checkpoint_root, checkpoint_name):
        save_prefix = os.path.join(checkpoint_root, checkpoint_name)
        self._saver.save(sess=self._session, save_path=save_prefix)

    def restore(self, save_path):
        self._saver.restore(self._session, save_path)

    @property
    def q_values(self):
        return self._q_values

    @property
    def replay_buffer(self):
        return self._replay_buffer

    @property
    def info_state_ph(self):
        return self._info_state_ph

    @property
    def loss(self):
        return self._last_loss_value

    @property
    def prev_timestep(self):
        return self._prev_timestep

    @property
    def prev_action(self):
        return self._prev_action

    @property
    def step_counter(self):
        return self._step_counter
