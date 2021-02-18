
import core.ArcadeMachine;
import java.util.Random;
import core.competition.CompetitionParameters;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author yuy
 */
public class Assignment1 {
 
    public static void main(String[] args)
    {
        //Available controllers:
    	String depthfirstController = "controllers.depthfirst.Agent";
    	String limitdepthfirstController = "controllers.limitdepthfirst.Agent";
        String AstarController = "controllers.Astar.Agent";
        String sampleMCTSController = "controllers.sampleMCTS.Agent";

        boolean visuals = true; // set to false if you don't want to see the game
        int seed = new Random().nextInt(); // seed for random
         
        
        /****** Task 1 ******/
        CompetitionParameters.ACTION_TIME = 10000; // set to the time that allow you to do the depth first search
        ArcadeMachine.runOneGame("examples/gridphysics/bait.txt", "examples/gridphysics/bait_lvl0.txt", true, depthfirstController, null, seed, false);



        /****** Task 2 ******/
        CompetitionParameters.ACTION_TIME = 100; // no time for finding the whole path
        ArcadeMachine.runOneGame("examples/gridphysics/bait.txt", "examples/gridphysics/bait_lvl0.txt", true, limitdepthfirstController, null, seed, false);

        /****** Task 3 ******/
        CompetitionParameters.ACTION_TIME = 4000; // no time for finding the whole path
        ArcadeMachine.runOneGame("examples/gridphysics/bait.txt", "examples/gridphysics/bait_lvl0.txt", true, AstarController, null, seed, false);
        ArcadeMachine.runOneGame("examples/gridphysics/bait.txt", "examples/gridphysics/bait_lvl1.txt", true, AstarController, null, seed, false);
        ArcadeMachine.runOneGame("examples/gridphysics/bait.txt", "examples/gridphysics/bait_lvl2.txt", true, AstarController, null, seed, false);
        ArcadeMachine.runOneGame("examples/gridphysics/bait.txt", "examples/gridphysics/bait_lvl3.txt", true, AstarController, null, seed, false);
        ArcadeMachine.runOneGame("examples/gridphysics/bait.txt", "examples/gridphysics/bait_lvl4.txt", true, AstarController, null, seed, false);


        /****** Task 4 ******/
        CompetitionParameters.ACTION_TIME = 100; // no time for finding the whole path
        ArcadeMachine.runOneGame("examples/gridphysics/bait.txt", "examples/gridphysics/bait_lvl0.txt", true, sampleMCTSController, null, seed, false);

    }   
}
