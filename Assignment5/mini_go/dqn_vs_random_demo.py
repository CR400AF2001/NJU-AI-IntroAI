from absl import logging, flags, app
from environment.GoEnv import Go
import time, os
import numpy as np
from algorimths.dqn import DQN
import tensorflow as tf

FLAGS = flags.FLAGS

flags.DEFINE_integer("num_train_episodes", 10000,
                     "Number of training episodes for each base policy.")
flags.DEFINE_integer("num_eval", 1000,
                     "Number of evaluation episodes")
flags.DEFINE_integer("eval_every", 2000,
                     "Episode frequency at which the agents are evaluated.")
flags.DEFINE_integer("learn_every", 128,
                     "Episode frequency at which the agents learn.")
flags.DEFINE_integer("save_every", 2000,
                     "Episode frequency at which the agents save the policies.")
flags.DEFINE_list("hidden_layers_sizes", [
    128, 128
], "Number of hidden units in the Q-net.")
flags.DEFINE_integer("replay_buffer_capacity", int(5e4),
                     "Size of the replay buffer.")
flags.DEFINE_integer("reservoir_buffer_capacity", int(2e6),
                     "Size of the reservoir buffer.")


def main(unused_argv):
    begin = time.time()
    env = Go()
    info_state_size = env.state_size
    num_actions = env.action_size

    hidden_layers_sizes = [int(l) for l in FLAGS.hidden_layers_sizes]
    kwargs = {
        "replay_buffer_capacity": FLAGS.replay_buffer_capacity,
        "epsilon_decay_duration": int(0.6*FLAGS.num_train_episodes),
        "epsilon_start": 0.8,
        "epsilon_end": 0.001,
        "learning_rate": 1e-3,
        "learn_every": FLAGS.learn_every,
        "batch_size": 128,
        "max_global_gradient_norm": 10,
    }
    import agent.agent as agent
    ret = [0]
    max_len = 2000

    with tf.Session() as sess:
        # agents = [DQN(sess, _idx, info_state_size,
        #                   num_actions, hidden_layers_sizes, **kwargs) for _idx in range(2)]  # for self play
        agents = [DQN(sess, 0, info_state_size,
                          num_actions, hidden_layers_sizes, **kwargs), agent.RandomAgent(1)]
        sess.run(tf.global_variables_initializer())

        # train the agent
        for ep in range(FLAGS.num_train_episodes):
            if (ep + 1) % FLAGS.eval_every == 0:
                losses = agents[0].loss
                logging.info("Episodes: {}: Losses: {}, Rewards: {}".format(ep + 1, losses, np.mean(ret)))
                with open('log_{}_{}'.format(os.environ.get('BOARD_SIZE'), begin), 'a+') as log_file:
                    log_file.writelines("{}, {}\n".format(ep+1, np.mean(ret)))
            if (ep + 1) % FLAGS.save_every == 0:
                if not os.path.exists("saved_model"):
                    os.mkdir('saved_model')
                agents[0].save(checkpoint_root='saved_model', checkpoint_name='{}'.format(ep+1))
            time_step = env.reset()  # a go.Position object
            while not time_step.last():
                player_id = time_step.observations["current_player"]
                agent_output = agents[player_id].step(time_step)
                action_list = agent_output.action
                time_step = env.step(action_list)
            for agent in agents:
                agent.step(time_step)
            if len(ret) < max_len:
                ret.append(time_step.rewards[0])
            else:
                ret[ep % max_len] = time_step.rewards[0]

        # evaluated the trained agent
        agents[0].restore("saved_model/10000")
        ret = []
        for ep in range(FLAGS.num_eval):
            time_step = env.reset()
            while not time_step.last():
                player_id = time_step.observations["current_player"]
                if player_id == 0:
                    agent_output = agents[player_id].step(time_step, is_evaluation=True, add_transition_record=False)
                else:
                    agent_output = agents[player_id].step(time_step)
                action_list = agent_output.action
                time_step = env.step(action_list)

            # Episode is over, step all agents with final info state.
            # for agent in agents:
            agents[0].step(time_step, is_evaluation=True, add_transition_record=False)
            agents[1].step(time_step)
            ret.append(time_step.rewards[0])
        print(np.mean(ret))

    print('Time elapsed:', time.time()-begin)


if __name__ == '__main__':
    app.run(main)
