package controllers.Astar;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Random;

import core.game.Observation;
import core.game.StateObservation;
import core.player.AbstractPlayer;
import ontology.Types;
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

    ArrayList<StateObservation> pastState = new ArrayList<StateObservation>();                      //存储所有的走过的状态
    ArrayList<StateObservation> targetPastState = new ArrayList<StateObservation>();                //存储当前节点的走过的状态

    Comparator<Node> OrderDistance = Comparator.comparingDouble(o -> o.score);
    PriorityQueue<Node> openState = new PriorityQueue<Node>(OrderDistance);             //定义一个按局面评分比较的优先队列，用来存储还未展开的节点

    ArrayList<Types.ACTIONS> Actions = new ArrayList<Types.ACTIONS>();                           //存储走过的动作
    int now = -1;                                                                    //act函数中输出动作的数组下标
    Vector2d goalpos;                                                                //目标的位置
    Vector2d keypos;                                                                 //钥匙的位置
    double goal_keyDistance;                                                         //钥匙与目标之间的曼哈顿距离
    boolean hasKey = false;                                                          //是否找到钥匙
    int searchDepth = 32;                                                            //限制A*算法的搜索深度，当地图较大时也能得出结果
    /*
    除第一关外，后续的其他关卡在ACTION_TIME = 100的条件下均无法完成搜索，故采用searchDepth参数来限制搜索深度。经过对不同的searchDepth进行测试，
    当searchDepth = 28时后续关卡可以通过且用时较少，ACTION_TIME = 1500即可完成搜索，但通关所需的步数较多；当searchDepth = 32时通关所需步数较少，
    且搜索所需的时长相对较少，ACTION_TIME = 4000即可完成搜索，所以将searchDepth设置为32。
    */

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

    boolean pastEqualTest(StateObservation state){          //判断当前状态之前是否到达过
        for(StateObservation so : pastState){
            if(state.equalPosition(so)){
                return true;
            }
        }
        return false;
    }


    Node equalNode(StateObservation state){           //如果当前状态在待展开的节点所含的状态中，返回对应节点，否则返回null
        for(Node node : openState){
            if(state.equalPosition(node.stateObs)){
                return node;
            }
        }
        return null;
    }

    double distance(StateObservation stateObs, boolean hasKey){         //利用精灵的位置、目标的位置和钥匙的位置以及已走过的步数构造启发式函数distance
        Vector2d playerpos = stateObs.getAvatarPosition();              //精灵的位置
        if(hasKey){
            return Math.abs(goalpos.x - playerpos.x) + Math.abs(goalpos.y - playerpos.y) + (Actions.size() * 50);       //如果已经拿到钥匙
        }
        return Math.abs(playerpos.x - keypos.x) + Math.abs(playerpos.y - keypos.y) + goal_keyDistance + (Actions.size() * 50);    //如果还没拿到钥匙
    }


    void getAStarActions(StateObservation stateObs, ElapsedCpuTimer elapsedTimer){
        openState = new PriorityQueue<Node>(OrderDistance);                     //初始化待展开节点
        pastState = (ArrayList<StateObservation>) targetPastState.clone();      //初始化所有已走过状态为上轮搜索后实际走过的状态
        Actions = new ArrayList<Types.ACTIONS>();                               //初始化已走过的动作
        Node startNode = new Node(stateObs,distance(stateObs, hasKey), Actions, targetPastState, hasKey);       //新建初始节点并加入openState中
        openState.add(startNode);
        while(!openState.isEmpty()) {                           //只要还有待展开节点就继续搜索
            Node temp = openState.poll();                       //选取评分最优的节点temp
            Actions = (ArrayList<Types.ACTIONS>) temp.actions.clone();                      //将Actions初始化为temp节点储存的已走过动作集
            targetPastState = (ArrayList<StateObservation>) temp.pastState.clone();         //将targetPastState初始化为temp节点储存的已走过状态
            pastState.add(temp.stateObs);                                                   //将temp节点的状态加入所有已走过的状态
            targetPastState.add(temp.stateObs);                                             //将temp节点的状态加入当前节点的走过的状态
            if(Actions.size() == searchDepth){                  //如果达到搜索深度，则返回，按该最优节点存储的Actions执行动作
                return;
            }
            hasKey = temp.hasKey;                               //初始化hasKey为当前节点的hasKey
            if(!hasKey){                                        //如果没有钥匙，则判断精灵位置是否与钥匙位置相同，如果是则有钥匙了
                if(temp.stateObs.getAvatarPosition().equals(keypos)){
                    hasKey = true;
                }
            }
            for(Types.ACTIONS action : temp.stateObs.getAvailableActions()){        //尝试当前局面所有可能的动作
                StateObservation stCopy = temp.stateObs.copy();                     //新建一个当前状态的副本，用于模拟施加动作
                stCopy.advance(action);                                             //施加动作
                Actions.add(action);                                                //将当前动作加入已走过的动作
                if(stCopy.getGameWinner() == Types.WINNER.PLAYER_WINS) {            //如果胜利，则返回，按Actions执行动作
                    return; // 最终的序列步骤在aStarAction中
                }
                if(stCopy.isGameOver() || pastEqualTest(stCopy)) {                  //如果如果动作施加后的状态之前已经到达过或者游戏失败，则尝试其他动作
                    Actions.remove(Actions.size() - 1);
                    continue;
                }
                Node equalNode = equalNode(stCopy);                         //搜索openState中与动作施加后的状态相同的节点
                if(equalNode != null){                                      //如果存在状态相同的节点
                    if(distance(stCopy,hasKey) < equalNode.score){          //如果当前走法优于之前的走法，则更新节点
                        openState.remove(equalNode);
                        openState.add(new Node(stCopy,distance(stCopy,hasKey),Actions,targetPastState, hasKey));
                    }
                    Actions.remove(Actions.size() - 1);
                }
                else{                                                       //动作施加后的是一个新的状态，加入新状态的节点
                    openState.add(new Node(stCopy,distance(stCopy,hasKey),Actions,targetPastState, hasKey));
                    Actions.remove(Actions.size() - 1);
                }
            }
        }
    }

    /**
     * Picks an action. This function is called every game step to request an
     * action from the player.
     * @param stateObs Observation of the current state.
     * @param elapsedTimer Timer when the action returned is due.
     * @return An action for the current state
     */
    public Types.ACTIONS act(StateObservation stateObs, ElapsedCpuTimer elapsedTimer) {


        if(Actions.size() == 0){
            goalpos = stateObs.getImmovablePositions()[1].get(0).position;
            keypos = stateObs.getMovablePositions()[0].get(0).position;
            goal_keyDistance = Math.abs(goalpos.x - keypos.x) + Math.abs(goalpos.y - keypos.y);
        }                   //初始化目标位置、钥匙位置和钥匙与目标之间的曼哈顿距离
        now++;              //更新下标
        if(now == Actions.size()){              //如果还未搜索或搜索返回的动作集已经执行完，则基于当前状态继续搜索，并将下标置为0
            getAStarActions(stateObs,elapsedTimer);
            now = 0;
        }
        return Actions.get(now);                //返回动作
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
