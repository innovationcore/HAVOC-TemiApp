# HAVOC: Healthcare Assistant with Video, Olfaction, and Conversation

HAVOC is an advanced Android application designed to transform the Temi robot into an autonomous healthcare assistant. The project leverages Large Language Models (LLMs), real-time communication, and environmental sensor integration to create a robot capable of navigating complex environments, interacting naturally with people, and performing environmental monitoring tasks.

## Features

- **Dual LLM-Powered Intelligence**: The robot's intelligence is driven by a two-tiered LLM system, using [CAAI's LLM-Factory](https://caai.ai.uky.edu/services/llm-factory/):
  - **Proactive Planning**: A "Planner" model analyzes conversation history and the robot's current context to generate a logical, step-by-step action plan (e.g., moving to a new location, speaking, etc.).
  - **Natural Conversation**: A "Talker" model enables fluid conversations with users. It gracefully concludes interactions by executing a tool call whenever the conversation is over or the user gives a direct command.
- **Centralized Environmental & Safety Monitoring**: HAVOC continuously streams the robot's live video feed, positional coordinates, and raw smell sensor data via WebRTC to a companion repository, `HAVOC-Server`. This server acts as a centralized hub for all heavy environmental processing.
- **Autonomous Operation**: The robot operates on a schedule, automatically navigating from its charging base to its work location during operational hours (8 AM to 5 PM on weekdays) and returning to charge when its battery is low or at the end of the day.
- **Human Detection & Interaction**: The robot uses its built-in sensors to detect when a person is nearby for a sustained period and can proactively initiate a conversation.
- **Robust State-Driven Logic**: The robot's core behavior is managed by a formal state machine, ensuring its essential duties are predictably completed.

---

## Example Execution Flow

A typical "day in the life" of a Temi robot controlled by HAVOC unfolds as follows:

1.  **Initialization**: The robot begins in the `HomeBase` state, resting on its charging dock.

2.  **Shift Start**: At 8:00 AM on a weekday, a scheduled timer (`checkAndMoveTemi`) fires. The robot transitions to the `MovingToEntrance` state and navigates to its designated `work_location`.

3.  **On Duty**: Upon arrival, it enters the `Detecting` state, actively waiting for user interaction or other events.

4.  **LLM-Powered Task Execution**:
    * **User Interaction**: Evan stands in front of the robot for 1.5 seconds. This prolonged presence is detected, transitioning the robot's state to `LlmControl`. The robot initiates dialogue: "Hello! How can I help you?" using a `ConversationAction` to manage the interaction.
    * **Initial Command**: Evan says, "Can you ask Sam what time the meeting is?". The `Talker` LLM, operating within the `ConversationAction`, interprets this as a command requiring actions. It concludes its immediate conversation by saying, "Sure, I'll go ask Sam what time the meeting is," and signals the `Planner` to create a plan.
    * **First Plan**: Based on the conversation log, the `Planner` generates its first action plan. The robot then begins executing it.
        ```json
        [
          {"type": "move", "destination": "Sam"},
          {"type": "speak", "message": "Hello Sam, what time is the meeting?", "wait_for_response": true}
        ]
        ```
        > The `speak` action's `wait_for_response: true` flag is critical here; it ensures the system uses a `ConversationAction` to capture the response, rather than just making an announcement.
    * **Dynamic Re-planning**: Sam replies, "I'm not sure, it's Cody's meeting." This new information is processed by the `Talker` LLM, which again concludes the conversation ("Okay, I'll go ask Cody.") and triggers the `Planner`. The `Planner` discards the old plan and generates a new one based on the *entire history* of actions and conversations.
    * **Second Plan**: The robot now executes the updated plan.
        ```json
        [
          {"type": "move", "destination": "Cody"},
          {"type": "speak", "message": "Hello Cody, what time is the meeting?", "wait_for_response": true}
        ]
        ```
    * **Information Acquired**: Cody responds, "It's at 3 PM." The `Talker` LLM understands the core question has been answered, ends the conversation with an acknowledgement, and triggers a final replan to complete the original user's request.
    * **Final Plan**: The `Planner` generates the last plan to report back.
        ```json
        [
          {"type": "move", "destination": "Evan"},
          {"type": "speak", "message": "Sam didn't know about the meeting, so I asked Cody. The meeting is at 3pm.", "wait_for_response": false}
        ]
        ```
5.  **Task Completion**: After delivering the message to Evan, the action queue is empty. The robot transitions back to `MovingToEntrance` and re-enters the `Detecting` state, ready for the next interaction.

6.  **Scheduled Patrol**: At the top of every hour, a recurring trigger moves the robot into the `Patrolling` state. It completes its predefined route and then returns to the `Detecting` state at the entrance.

7.  **End of Day**: At 5:00 PM, the `checkAndMoveTemi` logic sends the robot home. It transitions to `MovingToHome`, navigates to its charging dock, and enters the `HomeBase` state for the night.

---

## Getting Started

### Prerequisites

-   An Android development environment (Android Studio).
-   A Temi Robot with the developer SDK enabled.
-   Access to a WebRTC signaling server, in this case `HAVOC-Server`.
-   An API key for LLM-Factory, or any other OpenAI compatible API.
-   A compatible USB smell sensor (if using this feature).

### Setup and Configuration

This project uses a properties file to manage all environment-specific variables and API keys.

1.  **Clone the Repository**
    ```bash
    git clone https://github.com/innovationcore/HAVOC-TemiApp.git
    ```

2.  **Create the Configuration File**
  -   In the project, navigate to `app/src/main/res/raw/`.
  -   Copy the `example_config.properties` file and rename the copy to `config.properties`.

3.  **Edit `config.properties`**
  -   Open the `config.properties` file and fill in the values for your specific environment.
  -   **`home_location` / `work_location`**: These must exactly match the names of saved locations in your Temi robot's memory.
  -   **`patrol_locations`**: A comma-separated list of saved locations for the robot to patrol.
  -   **`webrtc_server_url`**: The full URL to your WebRTC signaling server (HAVOC-Server).
  -   **`llm_api_key`**: Your private API key for the LLM service.
  -   **`planner_url` / `talker_url`**: The API endpoints for your Planner and Talker LLMs.

---

## Core Logic: The State Machine

The robot's basic operational logic is governed by a state machine defined in the `statemachine` package.

![State Machine Diagram](https://github.com/user-attachments/assets/f1a883cc-c20e-44c5-9a16-2ac3c6ce37f7)

### State Descriptions

- **HomeBase**: The default, idle state. The robot remains at its charging station, waiting until its work shift begins.
- **MovingToEntrance**: A transitional state where the robot is actively navigating from its home base to its designated `work_location`.
- **Detecting**: The primary "on-duty" state. The robot is idle at its `work_location` and actively monitors for people to interact with. From this state, it can transition to `LlmControl` if a person is detected, `Patrolling` if it's time for a scheduled patrol, or `MovingToHome` if the battery is low or the work shift is over.
- **Patrolling**: The robot autonomously executes a patrol route, streaming sensor data and video. After completing the patrol, it returns to the `Detecting` state.
- **LlmControl**: Entered when the robot detects a person and begins an interaction. The Planner and ConversationAction components manage the conversation and execute resulting commands. The state machine transitions out once the action queue is empty.
- **MovingToHome**: A transitional state where the robot navigates back to its home base, triggered by low battery or the end of a work shift.

---

## Codebase Structure

The HAVOC codebase is organized into several packages, each handling specific functionality.

### Package: `edu.uky.ai.havoc`

- **MainActivity.java**
  - **Description**: The central orchestrator of the HAVOC application, managing the Android activity lifecycle and coordinating all core components. It initializes the Temi robot, sets up listeners for events (e.g., speech recognition, navigation, detection), and manages permissions. This is where both state transitions happen and where actions generated by the Planner LLM are executed.
- **Config.java**
  - **Description**: A static utility class responsible for loading and providing access to all application configurations. It reads key-value pairs from `config.properties` and also loads the raw text for system prompts from files within the `res/raw` directory.

### Package: `edu.uky.ai.havoc.statemachine`

- **RogueTemiCore.java**
  - **Description**: Defines the core state machine logic for the robot, implemented as a finite state machine with six states. This file was generated using the UMPLE modeling language and serves as the foundational state management logic.
- **RogueTemiExtended.java**
  - **Description**: Extends `RogueTemiCore` to add Temi-specific functionality and UI updates, such as changing the robot’s face image based on its state and managing WebRTC streaming during patrols. It overrides key transition methods to include additional behavior, like initiating patrols or updating the UI.

### Package: `edu.uky.ai.havoc.streaming`

- **SmellSensorUtils.java**
  - **Description**: Provides low-level logic to interface with a custom USB smell sensor, using the `usb-serial-for-android` library. It initializes the USB connection, reads sensor data, and formats it for streaming via WebRTC.
- **DataSendingBackgroundExecutor.java**
  - **Description**: Manages a background task that periodically sends the robot’s position data and the most recent smell data via the WebRTC data channel.
- **SmellBackgroundExecutor.java**
  - **Description**: Runs a background task to periodically read data from the smell sensor using `SmellSensorUtils`.
- **WebRTCStreamingManager.java**
  - **Description**: Manages real-time video and data streaming using WebRTC. It establishes a peer connection with a remote client via a signaling server, streams the robot’s camera feed, and sends position and smell sensor data through a data channel.

### Package: `edu.uky.ai.havoc.llm`

- **LlmWrapper.java**
  - **Description**: A utility class that encapsulates the configuration and state for interacting with LLMs. It manages the system prompt, conversation history, and response data.
- **LlmConnector.java**
  - **Description**: Handles communication with the LLM API, sending queries and processing responses. It defines tools (`parse_plan`, `conversation_over`) for structured outputs and supports different tool usage modes.
- **Planner.java**
  - **Description**: Generates action plans for the robot using an LLM (Deepseek-R1 by default). It constructs a prompt based on action history and current location, queries the LLM, and parses the response into a list of `TemiAction` objects.

### Package: `edu.uky.ai.havoc.actions`

- **TemiAction.java**
  - **Description**: An interface defining the contract for actions the robot can execute.
- **ActionManager.java**
  - **Description**: Manages the execution of `TemiAction` objects in a sequential manner using a single-threaded executor.
- **ActionCompletionListener.java**
  - **Description**: An interface defining a callback for action completion, allowing actions to notify listeners of their status.
- **MoveAction.java**
  - **Description**: Implements a `TemiAction` for navigating the robot to a specified location, handling completion or failure.
- **SpeakActionWithoutResponse.java**
  - **Description**: Implements a `TemiAction` for making the robot speak a message without waiting for a user response.
- **ConversationAction.java**
  - **Description**: Implements a `TemiAction` for handling interactive conversations with users. It uses an LLM (LLaMa 3 by default) to generate responses, supports multi-turn dialogues, and ends conversations gracefully using a tool call whenever a direct command is received or the conversation naturally ends.
