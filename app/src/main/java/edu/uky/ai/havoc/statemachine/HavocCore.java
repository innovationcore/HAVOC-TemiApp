package edu.uky.ai.havoc.statemachine;
//%% NEW FILE HavocTemiCore BEGINS HERE %%

/*PLEASE DO NOT EDIT THIS CODE*/
/*This code was generated using the UMPLE 1.35.0.7523.c616a4dce modeling language!*/



// line 2 "model.ump"
// line 50 "model.ump"
public class HavocCore
{

    //------------------------
    // MEMBER VARIABLES
    //------------------------

    //HavocCore State Machines
    public enum State { HomeBase, MovingToEntrance, Detecting, Patrolling, LlmControl, MovingToHome }
    private State state;

    //------------------------
    // CONSTRUCTOR
    //------------------------

    public HavocCore()
    {
        setState(State.HomeBase);
    }

    //------------------------
    // INTERFACE
    //------------------------

    public String getStateFullName()
    {
        String answer = state.toString();
        return answer;
    }

    public State getState()
    {
        return state;
    }

    public boolean timeBetween9amAnd5pm()
    {
        boolean wasEventProcessed = false;

        State aState = state;
        switch (aState)
        {
            case HomeBase:
                setState(State.MovingToEntrance);
                wasEventProcessed = true;
                break;
            default:
                // Other states do respond to this event
        }

        return wasEventProcessed;
    }

    public boolean arrivedAtEntrance()
    {
        boolean wasEventProcessed = false;

        State aState = state;
        switch (aState)
        {
            case MovingToEntrance:
                setState(State.Detecting);
                wasEventProcessed = true;
                break;
            default:
                // Other states do respond to this event
        }

        return wasEventProcessed;
    }

    public boolean personDetected()
    {
        boolean wasEventProcessed = false;

        State aState = state;
        switch (aState)
        {
            case Detecting:
                setState(State.LlmControl);
                wasEventProcessed = true;
                break;
            default:
                // Other states do respond to this event
        }

        return wasEventProcessed;
    }

    public boolean timeBetween5pmAnd9am()
    {
        boolean wasEventProcessed = false;

        State aState = state;
        switch (aState)
        {
            case Detecting:
                setState(State.MovingToHome);
                wasEventProcessed = true;
                break;
            default:
                // Other states do respond to this event
        }

        return wasEventProcessed;
    }

    public boolean timeToPatrol()
    {
        boolean wasEventProcessed = false;

        State aState = state;
        switch (aState)
        {
            case Detecting:
                setState(State.Patrolling);
                wasEventProcessed = true;
                break;
            default:
                // Other states do respond to this event
        }

        return wasEventProcessed;
    }

    public boolean patrolComplete()
    {
        boolean wasEventProcessed = false;

        State aState = state;
        switch (aState)
        {
            case Patrolling:
                setState(State.Detecting);
                wasEventProcessed = true;
                break;
            default:
                // Other states do respond to this event
        }

        return wasEventProcessed;
    }

    public boolean emptyQueue()
    {
        boolean wasEventProcessed = false;

        State aState = state;
        switch (aState)
        {
            case LlmControl:
                setState(State.MovingToEntrance);
                wasEventProcessed = true;
                break;
            default:
                // Other states do respond to this event
        }

        return wasEventProcessed;
    }

    public boolean arrivedAtHome()
    {
        boolean wasEventProcessed = false;

        State aState = state;
        switch (aState)
        {
            case MovingToHome:
                setState(State.HomeBase);
                wasEventProcessed = true;
                break;
            default:
                // Other states do respond to this event
        }

        return wasEventProcessed;
    }

    private void setState(State aState)
    {
        state = aState;

        // entry actions and do activities
        switch(state)
        {
            case HomeBase:
                // line 7 "model.ump"
                stateNotify("HomeBase");
                break;
            case MovingToEntrance:
                // line 13 "model.ump"
                stateNotify("MovingToEntrance");
                break;
            case Detecting:
                // line 19 "model.ump"
                stateNotify("Detecting");
                break;
            case Patrolling:
                // line 27 "model.ump"
                stateNotify("Patrolling");
                break;
            case LlmControl:
                // line 33 "model.ump"
                stateNotify("LlmControl");
                break;
            case MovingToHome:
                // line 39 "model.ump"
                stateNotify("MovingToHome");
                break;
        }
    }

    public void delete()
    {}

    // line 44 "model.ump"
    public boolean stateNotify(String node){
        return true;
    }

}