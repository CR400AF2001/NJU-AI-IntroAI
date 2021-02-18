/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package controllers.learningmodel;

import core.game.StateObservation;
import java.util.HashMap;
import java.util.Random;
import ontology.Types;
import weka.classifiers.Classifier;
import weka.classifiers.trees.REPTree;
import weka.core.Instance;
import weka.core.Instances;
/**
 *
 * @author yuy
 */
public class QPolicy {
    protected double m_epsilon=0.3;
    protected Classifier m_c;
    protected Instances m_dataset;
    protected Random m_rnd;
    protected int m_numActions;
    
    
    public QPolicy(int N_ACTIONS){
        m_numActions = N_ACTIONS;
        m_dataset = RLDataExtractor.datasetHeader();
        m_rnd = new Random();
        m_c = null;
    }
    
    public void setEpsilon(double epsilon){
        m_epsilon = epsilon;
    }
    
    // max Q action without epsilon-greedy 
    public int getActionNoExplore(double[] feature) throws Exception{
        double[] Q = getQArray(feature);
        
        // find best action according to Q value
        int bestaction = 0;
        for(int action=1; action<m_numActions; action++){
            if( Q[bestaction] < Q[action] ){
                bestaction = action;
            }
        }
        // among the same best actions, choose a random one
        int sameactions =0;
        for(int action=bestaction+1; action<m_numActions; action++){
            if(Q[bestaction] == Q[action]){
                sameactions++;
                if( m_rnd.nextDouble() < 1.0/(double)sameactions )
                    bestaction = action;
            }
        }
        
        return bestaction;
    }
    
    // max Q action with epsilon-greedy 
    public int getAction(double[] feature) throws Exception{
        double[] Q = getQArray(feature);
        
        // find best action according to Q value
        int bestaction = 0;
        for(int action=1; action<m_numActions; action++){
            if( Q[bestaction] < Q[action] ){
                bestaction = action;
            }
        }
        // among the same best actions, choose a random one
        int sameactions =0;
        for(int action=bestaction+1; action<m_numActions; action++){
            if(Q[bestaction] == Q[action]){
                sameactions++;
                if( m_rnd.nextDouble() < 1.0/(double)sameactions )
                    bestaction = action;
            }
        }
        
        // epsilon greedy
        if( m_rnd.nextDouble() < m_epsilon ){
            bestaction = m_rnd.nextInt(m_numActions);
        }
        
        return bestaction;
    }
    
    public double getMaxQ(double[] feature) throws Exception{
        double[] Q = getQArray(feature);
        
        // find best action according to Q value
        int bestaction = 0;
        for(int action=1; action<m_numActions; action++){
            if( Q[bestaction] < Q[action] )
                bestaction = action;
        }
        
        return Q[bestaction];
    }
    
    public double[] getQArray(double[] feature) throws Exception{
        
        double[] Q = new double[m_numActions];
        
        // get Q value from the prediction model
        for(int action = 0; action<m_numActions; action++){
            feature[feature.length-2] = action;
            feature[feature.length-1] = Double.NaN;
            Q[action] = m_c == null ? 0 : m_c.classifyInstance(makeInstance(feature));
        }
        
        return Q;
    }
    
    public void fitQ(Instances data) throws Exception{
        if( m_c == null ){
            m_c = new weka.classifiers.trees.REPTree();
            ((REPTree)m_c).setMinNum(1);
            ((REPTree)m_c).setNoPruning(true);
        }
        m_c.buildClassifier(data);   
    }
        
    protected Instance makeInstance(double[] vector){
        Instance ins = new Instance(1,vector);
        ins.setDataset(m_dataset);
        return ins;
    }
}
