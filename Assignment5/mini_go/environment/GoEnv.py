import os
os.environ['BOARD_SIZE'] = '5'
from environment import go, coords
import collections, enum
import numpy as np


class TimeStep(
    collections.namedtuple(
        "TimeStep", ["observations", "rewards", "discounts", "step_type"])):
    """Returned with every call to `step` and `reset`.

    A `TimeStep` contains the data emitted by a game at each step of interaction.
    A `TimeStep` holds an `observation` (list of dicts, one per player),
    associated lists of `rewards`, `discounts` and a `step_type`.

    The first `TimeStep` in a sequence will have `StepType.FIRST`. The final
    `TimeStep` will have `StepType.LAST`. All other `TimeStep`s in a sequence will
    have `StepType.MID.

    Attributes:
      observations: a list of dicts containing observations per player.
      rewards: A list of scalars (one per player), or `None` if `step_type` is
        `StepType.FIRST`, i.e. at the start of a sequence.
      discounts: A list of discount values in the range `[0, 1]` (one per player),
        or `None` if `step_type` is `StepType.FIRST`.
      step_type: A `StepType` enum value.
    """
    __slots__ = ()

    def first(self):
        return self.step_type == StepType.FIRST

    def mid(self):
        return self.step_type == StepType.MID

    def last(self):
        return self.step_type == StepType.LAST

    def current_player(self):
        return self.observations["current_player"]


class StepType(enum.Enum):
    """Defines the status of a `TimeStep` within a sequence."""

    FIRST = 0  # Denotes the first `TimeStep` in a sequence.
    MID = 1  # Denotes any `TimeStep` in a sequence that is not FIRST or LAST.
    LAST = 2  # Denotes the last `TimeStep` in a sequence.

    def first(self):
        return self is StepType.FIRST

    def mid(self):
        return self is StepType.MID

    def last(self):
        return self is StepType.LAST


class Go(object):
    def __init__(self, flatten_board_state=True, discount_factor=1.0):
        self.__state = go.Position(komi=0.5)
        self.__flatten_state = flatten_board_state
        self.__discount_factor = discount_factor
        N = int(os.environ.get("BOARD_SIZE"))
        self.__state_size = N ** 2
        self.__action_size = self.__state_size + 1  # board size and an extra action for "pass"
        self.__num_players = 2

    @property
    def state_size(self):
        return self.__state_size

    @property
    def action_size(self):
        return self.__action_size

    @property
    def to_play(self):
        if self.__state.to_play == 1:  # BLACK (player 1)
            return 0
        else:  # -1 for WHITE (player 2)
            return 1

    @property
    def info_state(self):
        return np.add(self.__state.board, 1)

    def step(self, action):
        """
        In step function, the game of go proceeds with the action taken by the current player and returns a next tuple to the player who is to act next step

        :param action: a place to move for current player
        :return: return a tuple of (next_state, done, reward, info), where the reward for Black (the first player) is 1, -1 and 0.
        """
        # if action not in go.Position.all_legal_moves():  # the go engine will raise an IllegalMove error
        #     raise('Illegal move!')
        #     exit(1)
        # self.state.play_move(action)
        move = coords.from_flat(action)
        self.__state.play_move(move, mutate=True)
        observations = {"info_state": [], "legal_actions": [], "current_player": []}
        for i in range(2):
            # if self.to_play == i:
            if self.__flatten_state:
                _state = np.reshape(self.info_state, (self.__state_size,))
            else:
                _state = self.info_state
            observations["info_state"].append(_state)
            observations['legal_actions'].append(np.where(self.__state.all_legal_moves() == 1)[0])
            # else:
            #     observations["info_state"].append(None)
            #     observations['legal_actions'].append(None)
        observations['current_player'] = self.to_play
        if self.__state.is_game_over():
            return TimeStep(observations=observations, rewards=[self.__state.result(), -self.__state.result()],
                            discounts=[self.__discount_factor] * self.__num_players, step_type=StepType.LAST)
        else:
            return TimeStep(observations=observations, rewards=[0.0, 0.0],
                            discounts=[self.__discount_factor] * self.__num_players, step_type=StepType.MID)

    def reset(self):
        """
        reset the game at the beginning of the game to get an initial state
        :return: should reset the env and return a initial state
        """
        self.__state = go.Position(komi=0.5)
        if self.__flatten_state:
            _state = np.reshape(self.info_state, (self.__state_size,))
        else:
            _state = self.info_state
        observations = {"info_state": [_state, None],
                        "legal_actions": [np.where(self.__state.all_legal_moves() == 1)[0], None],
                        "current_player": self.to_play}
        return TimeStep(observations=observations, rewards=[0.0, 0.0],
                        discounts=[self.__discount_factor] * self.__num_players, step_type=StepType.FIRST)

    def get_all_legal_moves(self):
        return self.__state.all_legal_moves()

    def get_current_board(self):
        return self.__state
