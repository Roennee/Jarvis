package ch.jarvis.companion;
import org.junit.Test;
import static org.junit.Assert.*;
public class TurnFeedbackTest {
    @Test public void onlySlowPendingTurnPromptsOnce(){TurnFeedback f=new TurnFeedback();long t=f.begin(true);assertFalse(f.claimPrompt(t,2000));assertTrue(f.claimPrompt(t,2500));assertFalse(f.claimPrompt(t,4000));}
    @Test public void finishedAndOldTurnsCannotSpeak(){TurnFeedback f=new TurnFeedback();long old=f.begin(true);f.finish();assertFalse(f.claimPrompt(old,5000));long next=f.begin(true);assertFalse(f.claimPrompt(old,5000));assertTrue(f.claimPrompt(next,3000));}
    @Test public void silentTurnsNeverPrompt(){TurnFeedback f=new TurnFeedback();long t=f.begin(false);assertFalse(f.claimPrompt(t,20000));}
}
