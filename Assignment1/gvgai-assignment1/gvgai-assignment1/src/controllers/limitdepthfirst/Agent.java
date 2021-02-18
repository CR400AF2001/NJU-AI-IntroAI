package controllers.limitdepthfirst;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Random;

import core.game.Observation;
import core.game.StateObservation;
import core.player.AbstractPlayer;
import ontology.Types;
import ontology.Types.WINNER;
import tools.ElapsedCpuTimer;
import tools.Vector2d;

/**
 * Created with IntelliJ IDEA.
 * User: ssamot
 * Date: 14/11/13
 * Time: 21:45
 * This is a Java port from Tom Schaul's VGDL - https://github.com/schaul/py-vgdl
 */
public class Agent extends AbstractPlayer {

    /**
     * Random generator for the agent.
     */
    protected Random randomGenerator;

    /**
     * Observation grid.
     */
    protected ArrayList<Observation> grid[][];

    /**
     * block size
     */
    protected int block_size;

    ArrayList<StateObservation> pastState = new ArrayList<StateObservation>();          //存储走过的状态
    ArrayList<Types.ACTIONS> Actions = new ArrayList<Types.ACTIONS>();               //存储走过的动作
    ArrayList<Types.ACTIONS> bestAction = new ArrayList<Types.ACTIONS>();            //存储搜索过的路径中最优的动作
    double bestScore = 10000;                                            //已走过的路径的最优评分（值越小越好）
    boolean hasKey = false;                                              //是否已经拿到钥匙
    int singleSearchDepth = 0;                                           //当前搜索的深度
    int searchDepth = 5;                                                 //规定的受限搜索深度（综合搜索时间和准确性确定）
    Vector2d goalpos;                                                    //目标的位置
    Vector2d keypos;                                                     //钥匙的位置
    double goal_keyDistance;                                             //钥匙与目标之间的曼哈顿距离

    /**
     * Public constructor with state observation and time due.
     * @param so state observation of the current game.
     * @param elapsedTimer Timer for the controller creation.
     */
    public Agent(StateObservation so, ElapsedCpuTimer elapsedTimer)
    {
        randomGenerator = new Random();
        grid = so.getObservationGrid();
        block_size = so.getBlockSize();
    }

    boolean equalTest(StateObservation state){                  //判断当前状态之前是否到达过
        for(StateObservation so : pastState){
            if(state.equalPosition(so)){
                return true;
            }
        }
        return false;
    }


    double distance(StateObservation stateObs){                 //利用精灵的位置、目标的位置和钥匙的位置构造启发式函数distance
        Vector2d playerpos = stateObs.getAvatarPosition();          //精灵的位置
        if(hasKey){
            return Math.abs(goalpos.x - playerpos.x) + Math.abs(goalpos.y - playerpos.y);       //如果已经拿到钥匙，则返回精灵与目标的曼哈顿距离
        }
        else{
            for(StateObservation so : pastState){                     //如果在当前搜索的走过的状态中，精灵已经到过钥匙所在的位置，则返回精灵与目标的曼哈顿距离
                if(so.getAvatarPosition().equals(keypos)){
                    return Math.abs(goalpos.x - playerpos.x) + Math.abs(goalpos.y - playerpos.y);
                }
            }
            return Math.abs(playerpos.x - keypos.x) + Math.abs(playerpos.y - keypos.y) + goal_keyDistance;
            //否则精灵无钥匙，返回精灵与目标的曼哈顿距离和钥匙与目标的曼哈顿距离的和
        }
    }


    void getLimitDepthFirstActions(StateObservation stateObs, ElapsedCpuTimer elapsedTimer){
        pastState.add(stateObs);                                        //将当前状态加入已走过的状态中
        singleSearchDepth++;                                            //搜索深度加一
        for(Types.ACTIONS action : stateObs.getAvailableActions()){         //尝试当前局面所有可能的动作
            StateObservation stCopy = stateObs.copy();                      //新建一个当前状态的副本，用于模拟施加动作
            stCopy.advance(action);                                         //施加动作
            Actions.add(action);                                            //将当前动作加入已走过的动作
            if(singleSearchDepth == searchDepth){                           //如果当前搜索的深度等于受限的深度
                double score = distance(stateObs);                          //根据启发式函数计算当前局面的评分
                if(score < bestScore){                                      //如果评分小于之前的最优评分，则更新最优解
                    bestAction = (ArrayList<Types.ACTIONS>) Actions.clone();
                    bestScore = score;
                }
            }
            else if(stCopy.getGameWinner() == Types.WINNER.PLAYER_WINS) {        //如果还没到受限的搜索深度就已经胜利，则根据受限深度与当前搜索深度的差确定评分
                double score = -50 * (searchDepth - singleSearchDepth);
                if(score < bestScore) {                                     //如果评分小于之前的最优评分，则更新最优解
                    bestAction = (ArrayList<Types.ACTIONS>) Actions.clone();
                    bestScore = score;
                }
                Actions.remove(Actions.size() - 1);             //因为执行该动作后已胜利，故当前局面的其他动作已无搜索的必要，可以直接返回
                singleSearchDepth--;
                pastState.remove(pastState.size() - 1);
                return;
            }
            else if(equalTest(stCopy) || stCopy.isGameOver()){         //如果如果动作施加后的状态之前已经到达过或者游戏失败，不进行操作

            }
            else{                                                      //动作施加后的是一个新的状态
                getLimitDepthFirstActions(stCopy,elapsedTimer);        //递归进行深度优先搜索
            }
            Actions.remove(Actions.size() - 1);                 //移除当前施加的动作，尝试另外的动作
        }
        singleSearchDepth--;                                           //当前状态的搜索结束，返回上一层
        pastState.remove(pastState.size() - 1);
        return;
    }

    /**
     * Picks an action. This function is called every game step to request an
     * action from the player.
     * @param stateObs Observation of the current state.
     * @param elapsedTimer Timer when the action returned is due.
     * @return An action for the current state
     */
    public Types.ACTIONS act(StateObservation stateObs, ElapsedCpuTimer elapsedTimer) {

        ArrayList<Observation>[] npcPositions = stateObs.getNPCPositions();
        ArrayList<Observation>[] fixedPositions = stateObs.getImmovablePositions();
        ArrayList<Observation>[] movingPositions = stateObs.getMovablePositions();
        ArrayList<Observation>[] resourcesPositions = stateObs.getResourcesPositions();
        ArrayList<Observation>[] portalPositions = stateObs.getPortalsPositions();
        grid = stateObs.getObservationGrid();

        /*printDebug(npcPositions,"npc");
        printDebug(fixedPositions,"fix");
        printDebug(movingPositions,"mov");
        printDebug(resourcesPositions,"res");
        printDebug(portalPositions,"por");
        System.out.println();               */

        if(stateObs.getAvatarPosition().equals(keypos)){                //如果当前状态下精灵在钥匙的位置，则精灵拥有了钥匙
            hasKey = true;
        }
        if(singleSearchDepth == 0 && !hasKey){                          //初始化目标位置、钥匙位置和钥匙与目标之间的曼哈顿距离
            goalpos = stateObs.getImmovablePositions()[1].get(0).position;
            keypos = stateObs.getMovablePositions()[0].get(0).position;
            goal_keyDistance = Math.abs(goalpos.x - keypos.x) + Math.abs(goalpos.y - keypos.y);
        }
        singleSearchDepth = 0;                                          //初始化当前搜索深度，最佳评分，动作集以及最佳动作集
        bestScore = 10000;
        Actions = new ArrayList<Types.ACTIONS>();
        bestAction = new ArrayList<Types.ACTIONS>();
        pastState.add(stateObs);                                        //将当前状态加入已走过的状态中
        getLimitDepthFirstActions(stateObs, elapsedTimer);              //每一步都进行一次深度受限的深度优先搜索，并将最佳动作集存储在bestAction中
        return bestAction.get(0);                                       //执行最佳动作集的第一步
    }

    /**
     * Prints the number of different types of sprites available in the "positions" array.
     * Between brackets, the number of observations of each type.
     * @param positions array with observations.
     * @param str identifier to print
     */
    private void printDebug(ArrayList<Observation>[] positions, String str)
    {
        if(positions != null){
            System.out.print(str + ":" + positions.length + "(");
            for (int i = 0; i < positions.length; i++) {
                System.out.print(positions[i].size() + ",");
            }
            System.out.print("); ");
        }else System.out.print(str + ": 0; ");
    }

    /**
     * Gets the player the control to draw something on the screen.
     * It can be used for debug purposes.
     * @param g Graphics device to draw to.
     */
    public void draw(Graphics2D g)
    {
        int half_block = (int) (block_size*0.5);
        for(int j = 0; j < grid[0].length; ++j)
        {
            for(int i = 0; i < grid.length; ++i)
            {
                if(grid[i][j].size() > 0)
                {
                    Observation firstObs = grid[i][j].get(0); //grid[i][j].size()-1
                    //Three interesting options:
                    int print = firstObs.category; //firstObs.itype; //firstObs.obsID;
                    g.drawString(print + "", i*block_size+half_block,j*block_size+half_block);
                }
            }
        }
    }
}
