import java.lang.annotation.Repeatable;
import java.util.Random;

import core.ArcadeMachine;
import core.competition.CompetitionParameters;

/**
 * Created with IntelliJ IDEA.
 * User: Diego
 * Date: 04/10/13
 * Time: 16:29
 * This is a Java port from Tom Schaul's VGDL - https://github.com/schaul/py-vgdl
 */
public class Test
{

    public static void main(String[] args)
    {
        ArcadeMachine.playOneGame( "examples/gridphysics/bait.txt", "examples/gridphysics/bait_lvl3.txt", null, new Random().nextInt());
    }
}
