package controllers.depthfirst;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Random;

import core.game.Observation;
import core.game.StateObservation;
import core.player.AbstractPlayer;
import ontology.Types;
import ontology.Types.WINNER;
import tools.ElapsedCpuTimer;

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

    ArrayList<StateObservation> pastState = new ArrayList<StateObservation>();      //存储走过的状态
    ArrayList<Types.ACTIONS> Actions = new ArrayList<Types.ACTIONS>();           //存储走过的动作
    int now = 0;                                                     //act函数中输出动作的数组下标
    boolean flag = false;                                            //是否已搜索到路径

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


    boolean getDepthFirstActions(StateObservation stateObs, ElapsedCpuTimer elapsedTimer){
        pastState.add(stateObs);                                                //将当前状态加入已走过的状态
        for(Types.ACTIONS action : stateObs.getAvailableActions()){             //尝试当前局面所有可以的动作
            StateObservation stCopy = stateObs.copy();                          //新建一个当前状态的副本，用于模拟施加动作
            stCopy.advance(action);                                             //施加动作
            Actions.add(action);                                                //将当前动作加入已走过的动作
            if(stCopy.getGameWinner() == Types.WINNER.PLAYER_WINS){
                return true;                                                    //如果获胜则返回已找到
            }
            if(equalTest(stCopy) || stCopy.isGameOver()){
                Actions.remove(Actions.size() - 1);                      //如果动作施加后的状态之前已经到达过或者游戏失败，则尝试下一个动作
            }
            else{                                                               //动作施加后的是一个新的状态
                if(getDepthFirstActions(stCopy,elapsedTimer)){                  //递归进行深度优先搜索
                    return true;
                }
                else {
                    Actions.remove(Actions.size() - 1);                  //当前动作递归搜索失败，尝试下一个动作
                }
            }
        }
        pastState.remove(pastState.size() - 1);                          //当前局面没有办法成功，删除当前局面
        return false;
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

        if(!flag){
            flag = getDepthFirstActions(stateObs, elapsedTimer);           //如果没有搜索过则进行搜索，搜索成功后将flag置为true，并且已将路径存储在Actions中
        }

        if(now < Actions.size()){
            now++;
            return Actions.get(now - 1);                                   //搜索到路径后将Actions中的动作依次执行
        }
        return null;
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
