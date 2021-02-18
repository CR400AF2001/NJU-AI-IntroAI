import core.ArcadeMachine;
import core.competition.CompetitionParameters;

import java.util.Random;


public class Test
{

    public static void main(String[] args) throws Exception
    {
        String modelController = "controllers.learningmodel.Agent";
        
        int seed = new Random().nextInt(); // seed for random
        
        CompetitionParameters.ACTION_TIME = 1000; // set to the time that allows you to do the depth first search
        ArcadeMachine.runOneGame("examples/gridphysics/aliens.txt", "examples/gridphysics/aliens_lvl0.txt", true, modelController, null, seed, false);
        
    }
}
