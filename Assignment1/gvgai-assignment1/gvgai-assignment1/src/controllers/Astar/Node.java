package controllers.Astar;

import core.game.StateObservation;
import ontology.Types;

import java.util.ArrayList;

public class Node {
    public Node(StateObservation stateObs, double score, ArrayList<Types.ACTIONS> actions, ArrayList<StateObservation> pastState, boolean hasKey) {
        this.stateObs = stateObs.copy();
        this.score = score;
        this.actions = (ArrayList<Types.ACTIONS>) actions.clone();
        this.pastState = (ArrayList<StateObservation>) pastState.clone();
        this.hasKey = hasKey;
    }               //初始化

    StateObservation stateObs;                          //当前节点的状态
    double score;                                       //当前节点的评分
    ArrayList<Types.ACTIONS> actions;                   //当前节点的已走过的路径
    ArrayList<StateObservation> pastState;              //当前节点的已走过的状态
    boolean hasKey;                                     //当前节点是否已经拥有钥匙

    public Node parent;
}
