
package controllers.learningmodel;

import controllers.Heuristics.SimpleStateHeuristic;
import core.game.StateObservation;
import core.player.AbstractPlayer;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import ontology.Types;
import tools.ElapsedCpuTimer;
import tools.Recorder;
import weka.classifiers.Classifier;
import weka.core.Instance;

public class Agent extends AbstractPlayer {
    
    protected Classifier m_model;

    public Agent(StateObservation stateObs, ElapsedCpuTimer elapsedTimer) {
        try{
            ObjectInputStream input = new ObjectInputStream(new FileInputStream("model.model"));
            m_model = (Classifier) input.readObject();
            input.close();
        }catch(Exception exc){
            exc.printStackTrace();
        }
    }

    /**
     *
     * Learning based agent.
     *
     * @param stateObs Observation of the current state.
     * @param elapsedTimer Timer when the action returned is due.
     * @return An action for the current state
     */
    public Types.ACTIONS act(StateObservation stateObs, ElapsedCpuTimer elapsedTimer) {

        double[] features = Recorder.featureExtract(stateObs);
        
        Instance ins = new Instance(1, features); // wrap an instance for prediction
        ins.setDataset(Recorder.s_datasetHeader);
        
        Types.ACTIONS bestAction = null;
        try{
            double[] predictionDistribution = m_model.distributionForInstance(ins);
            double maxD = Double.NEGATIVE_INFINITY;
            for (Types.ACTIONS action : stateObs.getAvailableActions()) {
                int action_num = 0;
                if( Types.ACTIONS.ACTION_NIL == action) action_num = 0;
                if( Types.ACTIONS.ACTION_USE == action) action_num = 1;
                if( Types.ACTIONS.ACTION_LEFT == action) action_num = 2;
                if( Types.ACTIONS.ACTION_RIGHT == action) action_num = 3;
                if( predictionDistribution[action_num] > maxD ){
                    maxD = predictionDistribution[action_num];
                    bestAction = action;
                }
            }
        }catch(Exception exc){
            exc.printStackTrace();
        }

       // System.out.println("====================");
       return bestAction;
    }
}
