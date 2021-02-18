### Mini-AlphaGo 复现 ###

Repo 中提供了围棋（Game of The Go）环境 (修改自 https://github.com/tensorflow/minigo.git)，并封装为供强化学习训练的接口形式。其中的围棋有黑（BLACK）、白（WHITE）两种颜色棋子，黑子先行，并让半子给白子。

每一步中，对应的玩家选择落子位置，环境执行一步玩家的落子动作，返回一个 timestep 元组，包含落子后的棋盘状态以及对应的结果、是否结束等信息，玩家根据当前棋盘的情况选择落子。
玩家可以选择在当前棋盘有空余的地方落子，或者选择 PASS，当双方都依次选择 PASS 时，根据各自的棋子数量和吃掉的棋子数量计算输赢。

具体的交互过程可参考rl_loop.py，其中为两个均匀随机落子的对手对弈。
默认棋盘大小为 5X5 （可在普通台式机或笔记本上运行），由环境变量 "BOARD_SIZE" 指定（默认在 environment/GoEnv.py 文件头部规定）。
返回的棋盘描述为一维向量，环境初始化时可通过将 flatten_board_state 设置为 False，返回二维棋盘状态编码，0 代表白子，1 代表空位，2 代表黑子。
返回二维棋盘时，推荐使用卷积神经网络（CNN）来作为策略网络；默认采用 MLP 作为策略网络。

此 Repo中提供了一个 DQN、Policy Gradient 算法的 demo，DQN 算法在 algorithms/dqn.py 中，Policy Gradient 算法在 algorithms/policy_gradient.py 中。
参考dqn_vs_random_demo.py 和a2c_vs_random_demo.py 文件参考使用方法。

基于所提供的围棋环境，完成 AlphaGo 框架的搭建（此处不提供人类玩家棋谱，可使用随机初始化的策略作为 rollout policy）。

其中 python 要求3.6以上，先用 pip install -r requirements.txt 命令，安装依赖包。