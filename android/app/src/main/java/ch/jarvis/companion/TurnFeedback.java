package ch.jarvis.companion;

/** Suppresses late or duplicate wait prompts, and all prompts in silent mode. */
final class TurnFeedback {
    private long generation=0;
    private boolean pending=false,prompted=false;
    synchronized long begin(boolean conversation){generation++;pending=conversation;prompted=false;return generation;}
    synchronized boolean claimPrompt(long turn,long elapsedMillis){
        if(turn!=generation||!pending||prompted||elapsedMillis<2500)return false;
        prompted=true;return true;
    }
    synchronized void finish(){pending=false;generation++;}
}
