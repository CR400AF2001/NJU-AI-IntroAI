import random, collections
StepOutput = collections.namedtuple("step_output", ["action", "probs"])


class Agent(object):
    def __init__(self):
        pass

    def step(self, timestep):
        raise NotImplementedError


class RandomAgent(Agent):
    def __init__(self, _id):
        super().__init__()
        self.player_id = _id

    def step(self, timestep):
        cur_player = timestep.observations["current_player"]
        return StepOutput(action=random.choice(timestep.observations["legal_actions"][cur_player]), probs=1.0)
