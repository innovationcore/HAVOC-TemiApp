# HAVOC: Healthcare Assistant with Video, Olfaction, and Conversation

HAVOC (Healthcare Assistant with Video, Olfaction, and Conversation) is an advanced Android application designed to transform the Temi robot into an autonomous healthcare assistant. The project leverages Large Language Models (LLMs), real-time communication, and environmental sensor integration to create a robot capable of navigating complex environments, interacting naturally with people, and performing environmental monitoring tasks.

## Features

- **Dual LLM-Powered Intelligence**: The robot's intelligence is driven by a two-tiered LLM system, using CAAI's LLM-Factory:
  - **Proactive Planning**: A "Planner" model analyzes conversation history and the robot's current context to generate a logical, step-by-step action plan (e.g., moving to a new location, speaking, etc.).
  - **Natural Conversation**: A "Talker" model enables fluid, context-aware conversations with users. It can handle turn-by-turn dialogue and gracefully conclude interactions by executing a tool call whenever the conversation is over or the user gives a direct command.
- **Centralized Environmental & Safety Monitoring**: HAVOC continuously streams the robot's live video feed, positional coordinates, and raw smell sensor data via WebRTC to a companion repository, `HAVOC-Server`. This server acts as a centralized hub for all heavy environmental processing.
- **Autonomous Operation**: The robot operates on a schedule, automatically navigating from its charging base to its work location during operational hours (8 AM to 5 PM on weekdays) and returning to charge when its battery is low or at the end of the day.
- **Human Detection & Interaction**: In its active state, the robot uses its built-in sensors to detect when a person is nearby for a sustained period and can proactively initiate a conversation.
- **Robust State-Driven Logic**: The robot's behavior is managed by a formal state machine, ensuring its actions are predictable, reliable, and recoverable.

---

## Getting Started

### Prerequisites

-   An Android development environment (Android Studio).
-   A Temi Robot with the developer SDK enabled.
-   Access to a WebRTC signaling server.
-   An API key for LLM-Factory.
-   A compatible USB smell sensor (if using this feature).

### Setup and Configuration

This project uses a properties file to manage all environment-specific variables and API keys. This keeps sensitive information out of the codebase.

1.  **Clone the Repository**
    ```bash
    git clone [your-repository-url]
    ```

2.  **Create the Configuration File**
  -   In the project, navigate to `app/src/main/res/raw/`.
  -   Copy the `config.properties.example` file and rename the copy to `config.properties`.

3.  **Edit `config.properties`**
  -   Open the `config.properties` file and fill in the values for your specific environment.
  -   **`home_location` / `work_location`**: These must exactly match the names of the saved locations in your Temi robot's memory.
  -   **`patrol_locations`**: A comma-separated list of saved locations for the robot to patrol.
  -   **`webrtc_server_url`**: The full URL to your WebRTC signaling server.
  -   **`llm_api_key`**: Your private API key for the LLM service.
  -   **`planner_url` / `talker_url`**: The API endpoints for your Planner and Talker LLMs.

---

## Core Logic: The State Machine

The robot's entire operational logic is governed by a state machine defined in the `statemachine` package. This ensures that the robot is always in a well-defined state, and transitions between states are triggered by specific events, such as time of day, arrival at a location, or user interaction.

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
  - **Description**: The central orchestrator of the HAVOC application, managing the Android activity lifecycle and coordinating all core components. It initializes the Temi robot, sets up listeners for events (e.g., speech recognition, navigation, detection), and manages permissions. It also implements a periodic timer (`checkAndMoveTemi`) that evaluates the robot’s state, battery level, and time to trigger state transitions. The file handles user input, queues actions generated by the LLM, and ensures robust integration with the `ActionManager`, `Planner`, `StateManager`, and `WebRTCStreamingManager`.

### Package: `edu.uky.ai.havoc.statemachine`

- **RogueTemiCore.java**
  - **Description**: Defines the core state machine logic for the robot, implemented as a finite state machine with six states. It provides methods for state transitions triggered by events (e.g., `timeBetween9amAnd5pm`, `personDetected`) and ensures the robot’s behavior is predictable and recoverable. This file was generated using the UMPLE modeling language and serves as the foundational state management logic.
- **RogueTemiExtended.java**
  - **Description**: Extends `RogueTemiCore` to add Temi-specific functionality and UI updates, such as changing the robot’s face image based on its state and managing WebRTC streaming during patrols. It overrides key transition methods to include additional behavior, like initiating patrols or updating the UI.

### Package: `edu.uky.ai.havoc.streaming`

- **SmellSensorUtils.java**
  - **Description**: Provides low-level logic to interface with a custom USB smell sensor, using the `usb-serial-for-android` library. It initializes the USB connection, reads sensor data, and formats it for streaming via WebRTC.
- **DataSendingBackgroundExecutor.java**
  - **Description**: Manages a background task that periodically sends the robot’s position data to a remote client via the WebRTC data channel.
- **SmellBackgroundExecutor.java**
  - **Description**: Runs a background task to periodically read data from the smell sensor using `SmellSensorUtils`.
- **WebRTCStreamingManager.java**
  - **Description**: Manages real-time video and data streaming using WebRTC. It establishes a peer connection with a remote client via a signaling server, streams the robot’s camera feed, and sends position and smell sensor data through a data channel.

### Package: `edu.uky.ai.havoc.llm`

- **LlmWrapper.java**
  - **Description**: A utility class that encapsulates the configuration and state for interacting with LLMs. It manages the system prompt, conversation history, and response data.
- **LlmConnector.java**
  - **Description**: Handles communication with the LLM API, sending queries and processing responses. It defines tools (`parse_plan`, `conversation_over`) for structured outputs and supports different tool usage modes.
- **PromptLoader.java**
  - **Description**: A utility class for loading prompt text from raw resource files.
- **Planner.java**
  - **Description**: Generates action plans for the robot using an LLM. It constructs a prompt based on action history and current location, queries the LLM, and parses the response into a list of `TemiAction` objects.

### Package: `edu.uky.ai.havoc.actions`

- **ActionCompletionListener.java**
  - **Description**: An interface defining a callback for action completion, allowing actions to notify listeners of their status.
- **MoveAction.java**
  - **Description**: Implements a `TemiAction` for navigating the robot to a specified location, handling completion or failure.
- **TemiAction.java**
  - **Description**: An interface defining the contract for actions the robot can execute.
- **SpeakActionWithoutResponse.java**
  - **Description**: Implements a `TemiAction` for making the robot speak a message without waiting for a user response.
- **ActionManager.java**
  - **Description**: Manages the execution of `TemiAction` objects in a sequential manner using a single-threaded executor.
- **ConversationAction.java**
  - **Description**: Implements a `TemiAction` for handling interactive conversations with users. It uses an LLM to generate responses, supports multi-turn dialogues, and ends conversations gracefully using a tool call.

---

## Example Execution Flow

A typical "day in the life" of HAVOC unfolds as follows:

1. **Initialization**: The robot starts in the `HomeBase` state, on its charging dock.
2. **Shift Start**: At 8 AM on a weekday, the `checkAndMoveTemi` timer detects it’s time to work. The robot transitions to `MovingToEntrance` and navigates to the `work_location`.
3. **On Duty**: Upon arrival, it transitions to the `Detecting` state, waiting for events.
4. **User Interaction**: A person stands in front of the robot for 1.5 seconds, triggering a transition to `LlmControl`. The robot says, "Hello! How can I help you?" and uses the `ConversationAction` to handle dialogue.
5. **LLM-Powered Task**: The person asks, "Can you tell me where the cafeteria is?" The `Planner` generates a plan: `[{"type": "speak", "message": "Of course. Follow me."}, {"type": "move", "destination": "cafeteria"}]`. The robot executes these actions.
6. **Task Completion**: After arriving, the robot says, "We have arrived." The action queue empties, and the robot transitions back to `MovingToEntrance`, then `Detecting`.
7. **Scheduled Patrol**: At the top of the hour, the robot transitions to `Patrolling`, completes its route, and returns to `Detecting`.
8. **End of Day**: At 5 PM, the `checkAndMoveTemi` logic triggers a transition to `MovingToHome`. The robot returns to its charging dock, entering `HomeBase`.
