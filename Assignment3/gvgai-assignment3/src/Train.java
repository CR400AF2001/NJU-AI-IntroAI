import core.ArcadeMachine;
import core.game.Game;
import tools.Recorder;

import java.util.Random;


public class Train
{

    public static void main(String[] args) throws Exception
    {
        Recorder recorder  = new Recorder("AliensRecorder");
        
        Game.setRecorder(recorder);
        ArcadeMachine.playOneGame( "examples/gridphysics/aliens.txt", "examples/gridphysics/aliens_lvl0.txt", null, new Random().nextInt());
    }
}
