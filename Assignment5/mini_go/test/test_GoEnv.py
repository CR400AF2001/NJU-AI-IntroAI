import random

# import os
#
# os.environ['BOARD_SIZE'] = '7'
from environment.GoEnv import Go
import time

if __name__ == '__main__':
    begin = time.time()
    env = Go()
    for i in range(10):
        state = env.reset()  # a go.Position object
        while True:
            # cur_player = env.to_play
            _a = env.get_all_legal_moves()
            probs = _a / sum(_a)
            probs = probs.cumsum()
            selection = random.random()
            fcoord = probs.searchsorted(selection)
            n_s, done, rew, info = env.step(fcoord)
            print(fcoord, env.get_current_board())
            if done:
                break
    print('Time elapsed:', time.time()-begin)
