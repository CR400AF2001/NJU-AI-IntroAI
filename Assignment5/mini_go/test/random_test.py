from absl import logging, flags, app
from environment.GoEnv import Go
import time, os
import numpy as np
from agent.agent import RandomAgent
import tensorflow as tf

FLAGS = flags.FLAGS

flags.DEFINE_integer("num_train_episodes", 10,
                     "Number of training episodes for each base policy.")
flags.DEFINE_integer("num_eval", 10,
                     "Number of evaluation episodes")
flags.DEFINE_integer("eval_every", 2000,
                     "Episode frequency at which the agents are evaluated.")
flags.DEFINE_integer("learn_every", 128,
                     "Episode frequency at which the agents are evaluated.")
flags.DEFINE_list("hidden_layers_sizes", [
    128
], "Number of hidden units in the avg-net and Q-net.")
flags.DEFINE_integer("replay_buffer_capacity", int(2e5),
                     "Size of the replay buffer.")
flags.DEFINE_integer("reservoir_buffer_capacity", int(2e6),
                     "Size of the reservoir buffer.")


def main(unused_argv):
    begin = time.time()
    env = Go()
    agents = [RandomAgent(idx) for idx in range(2)]

    for ep in range(FLAGS.num_eval):
        time_step = env.reset()
        while not time_step.last():
            player_id = time_step.observations["current_player"]
            if player_id == 0:
                agent_output = agents[player_id].step(time_step)
            else:
                agent_output = agents[player_id].step(time_step)
            action_list = agent_output.action
            time_step = env.step(action_list)
            print(time_step.observations["info_state"][0])

        # Episode is over, step all agents with final info state.
        # for agent in agents:
        agents[0].step(time_step)
        agents[1].step(time_step)
        print(time_step.rewards, env.get_current_board())

    print('Time elapsed:', time.time()-begin)


if __name__ == '__main__':
    app.run(main)
