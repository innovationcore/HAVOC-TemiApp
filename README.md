# RogueTemi: An Autonomous Healthcare Assistant Robot

RogueTemi is an advanced Android application designed to transform the Temi robot into an autonomous healthcare assistant. The project leverages Large Language Models (LLMs) using the CAAI [LLM-Factory](https://caai.ai.uky.edu/services/llmfactory/) API to serve as Temi's brain, real-time communication, and [smell sensor](https://www.smart-nanotubes.com/) integration to create a robot capable of navigating complex environments, interacting naturally with people, and performing environmental monitoring tasks.

The primary goal of RogueTemi is to serve as a proof-of-concept for robotic assistants in healthcare settings. It can autonomously patrol facilities, provide information to patients and staff, and use a smell sensor to detect environmental hazards like ammonia, which is relevant for identifying human waste and ensuring patient hygiene.

## Features

- **Dual LLM-Powered Intelligence**: The robot's intelligence is driven by a two-tiered LLM system managed by the "LLM-Factory" tool:
  - **Proactive Planning**: A DeepSeek-R1 model acts as the "Planner." It analyzes conversation history and the robot's current context to generate a logical, step-by-step action plan (e.g., moving to a new location, speaking, etc.).
  - **Natural Conversation**: A LLaMA 3 model functions as the "Talker," enabling fluid, context-aware conversations with users. It can handle turn-by-turn dialogue and gracefully conclude interactions by executing a tool call whenever the conversation is over or the user gives a direct command.
- **Autonomous Operation**: The robot operates on a schedule, automatically navigating from its charging base to its work location during operational hours (e.g., 8 AM to 5 PM on weekdays) and returning to charge when its battery is low or at the end of the day.
- **Real-time Video & Data Streaming**: RogueTemi uses WebRTC to stream a live video feed from the robot's camera to a remote client. A WebRTC data channel is used to continuously send the robot's position coordinates and smell sensor readings for remote monitoring.
- **Environmental Smell Sensing**: The application interfaces with a USB smell sensor to collect and stream air quality data. This data is used as a proof-of-concept model to detect ammonia.
- **Human Detection & Interaction**: In its active state, the robot uses its built-in sensors to detect when a person is nearby for a sustained period and can proactively initiate a conversation by asking if they need help.
- **Robust State-Driven Logic**: The robot's behavior is managed by a formal state machine, ensuring its actions are predictable, reliable, and recoverable.
- **Remote Fall Detection Integration**: The streamed video feed is designed to be processed by a companion repository, temiStreamCatch, which analyzes the stream to detect human falls, adding a layer of patient safety monitoring.

## Getting Started

### Prerequisites

-   An Android development environment (Android Studio).
-   A Temi Robot with the developer SDK enabled.
-   Access to a WebRTC signaling server.
-   An API key for an LLM provider.
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


## Core Logic: The State Machine

The robot's entire operational logic is governed by a state machine defined in the `statemachine` package (`RogueTemiCore.java` and `RogueTemiExtended.java`). This ensures that the robot is always in a well-defined state, and transitions between states are triggered by specific events, such as time of day, arrival at a location, or user interaction.

![State Machine Diagram](https://github.com/user-attachments/assets/f1a883cc-c20e-44c5-9a16-2ac3c6ce37f7)

### State Descriptions

- **HomeBase**: The default, idle state. The robot remains at its charging station, home base, waiting until its work shift begins (e.g., after 8 AM).
- **MovingToEntrance**: A transitional state where the robot is actively navigating from its home base to its designated `WORKING_LOCATION`.
- **Detecting**: The primary "on-duty" state. The robot is idle at its `WORKING_LOCATION` and actively monitors for people to interact with. From this state, it can:
  - Transition to `LlmControl` if a person is detected.
  - Transition to `Patrolling` if it's time for a scheduled patrol.
  - Transition to `MovingToHome` if the battery is low or the work shift is over.
- **Patrolling**: The robot autonomously executes a patrol route, streaming smell sensor data and video to a server. After completing the patrol, it returns to the `Detecting` state.
- **LlmControl**: Entered when the robot detects a person and begins an interaction. The Planner (DeepSeek) and ConversationAction (LLaMA) components manage the conversation and execute resulting commands. The state machine transitions out once the action queue is empty.
- **MovingToHome**: A transitional state where the robot navigates back to its home base, triggered by low battery or the end of a work shift.

## Codebase Structure

The RogueTemi codebase is organized into several packages, each handling specific functionality. Below is a breakdown of the key files, organized by package.

### Package: `edu.uky.ai.roguetemi`

- **MainActivity.java**
  - **Description**: The central orchestrator of the RogueTemi application, managing the Android activity lifecycle and coordinating all core components. It initializes the Temi robot, sets up listeners for events (e.g., speech recognition, navigation, detection), and manages permissions for camera and audio access. It also implements a periodic timer (`checkAndMoveTemi`) that evaluates the robot’s state, battery level, and time to trigger state transitions, such as moving to the work location or returning to the home base. The file handles user input, queues actions generated by the LLM, and ensures robust integration with the `ActionManager`, `Planner`, `StateManager`, and `WebRTCStreamingManager`.
  - **Key Features**:
    - Initializes the Temi robot and registers listeners for ASR, NLP, navigation, and detection events.
    - Manages a queue of `TemiAction` objects for sequential execution.
    - Implements logic to handle navigation retries (e.g., turning 90 degrees on failure).
    - Coordinates WebRTC streaming and smell sensor data collection.
    - Maintains a history of interactions for the `Planner` to generate action plans.

### Package: `edu.uky.ai.roguetemi.statemachine`

- **RogueTemiCore.java**
  - **Description**: Defines the core state machine logic for RogueTemi, implemented as a finite state machine with six states: `HomeBase`, `MovingToEntrance`, `Detecting`, `Patrolling`, `LlmControl`, and `MovingToHome`. It provides methods for state transitions triggered by events (e.g., `timeBetween9amAnd5pm`, `personDetected`) and ensures the robot’s behavior is predictable and recoverable. This file was generated using the UMPLE modeling language and serves as the foundational state management logic.
  - **Key Features**:
    - Defines the state machine’s states and transitions using an enum (`State`).
    - Each transition method (e.g., `arrivedAtEntrance`, `patrolComplete`) checks the current state and updates it if valid, returning a boolean to indicate success.
    - Includes a `stateNotify` method for logging state changes.
    - Acts as a parent class for `RogueTemiExtended`, providing the base state machine logic.

- **RogueTemiExtended.java**
  - **Description**: Extends `RogueTemiCore` to add Temi-specific functionality and UI updates, such as changing the robot’s face image based on its state and managing WebRTC streaming during patrols. It overrides key transition methods to include additional behavior, like initiating patrols or updating the UI. This class integrates with the Temi robot’s API and the `WebRTCStreamingManager` to ensure seamless operation in the healthcare assistant context.
  - **Key Features**:
    - Overrides methods like `timeToPatrol` to initiate a patrol route with specific locations and configure WebRTC streaming.
    - Updates the robot’s face image (via `ImageView`) to reflect states like `Detecting` or `HomeBase`.
    - Manages streaming behavior, starting or stopping streams based on the robot’s state.
    - Logs state transitions for debugging and ensures integration with the Temi robot’s navigation and patrol capabilities.

### Package: `edu.uky.ai.roguetemi.streaming`

- **SmellSensorUtils.java**
  - **Description**: Provides low-level logic to interface with a custom USB smell sensor, using the `usb-serial-for-android` library. It initializes the USB connection, reads sensor data, and formats it for streaming via WebRTC. The class handles connection errors and ensures robust communication with the sensor.
  - **Key Features**:
    - Initializes the USB serial port with a baud rate of 115200 and standard parameters.
    - Reads sensor data into a buffer, processes it to extract valid readings, and forwards it to `WebRTCStreamingManager`.
    - Manages connection lifecycle, including opening and closing the port.

- **DataSendingBackgroundExecutor.java**
  - **Description**: Manages a background task that periodically sends the robot’s position data to a remote client via the WebRTC data channel. It uses a `ScheduledExecutorService` to run the task every 500 milliseconds, ensuring continuous updates during operation.
  - **Key Features**:
    - Schedules a task to retrieve and send the robot’s position using `temi.getPosition()`.
    - Handles exceptions to prevent task termination and supports starting/stopping the executor.

- **SmellBackgroundExecutor.java**
  - **Description**: Runs a background task to periodically read data from the smell sensor using `SmellSensorUtils`. It operates on a schedule (every 3 seconds after an initial 5-second delay) and ensures continuous sensor data collection during the robot’s operation.
  - **Key Features**:
    - Uses a `ScheduledExecutorService` to manage the sensor reading task.
    - Integrates with `SmellSensorUtils` to read and process sensor data.
    - Provides methods to start, stop, and check the status of the task.

- **WebRTCStreamingManager.java**
  - **Description**: Manages real-time video and data streaming using WebRTC. It establishes a peer connection with a remote client via a signaling server, streams the robot’s camera feed, and sends position and smell sensor data through a WebRTC data channel. The class handles connection lifecycle, including initialization, streaming, and cleanup, and provides callbacks for connection status changes.
  - **Key Features**:
    - Initializes a `PeerConnectionFactory` and sets up a video capturer using the device’s front-facing camera.
    - Creates a data channel to send JSON-formatted messages containing position and smell sensor data.
    - Communicates with a hardcoded signaling server (`SERVER_URL`) to exchange session descriptions (offer/answer).
    - Supports starting, stopping, and restarting the stream, with robust error handling for connection failures.
    - Integrates with `SmellBackgroundExecutor` to ensure continuous sensor data updates.

### Package: `edu.uky.ai.roguetemi.llm`

- **LlmWrapper.java**
  - **Description**: A utility class that encapsulates the configuration and state for interacting with LLMs. It manages the system prompt, conversation history, and response data, providing methods to add prompts, set required tools, and clear history while preserving the system prompt.
  - **Key Features**:
    - Maintains a `JSONArray` for conversation history and a `JSONObject` for system prompt and responses.
    - Escapes special characters in prompts to prevent JSON parsing issues.
    - Supports configuration of LLM endpoint URL, model, and required tools.

- **LlmConnector.java**
  - **Description**: Handles communication with the LLM API, sending queries and processing responses. It defines tools (`parse_plan`, `conversation_over`) for structured outputs and supports different tool usage modes (none, required, auto). The class uses asynchronous HTTP requests with robust error handling and logging.
  - **Key Features**:
    - Sends POST requests to the LLM API with JSON payloads, including messages, model, and tool definitions.
    - Defines tools for parsing plans into JSON actions and signaling conversation completion.
    - Uses `AsyncTask` for network operations and includes timeout settings for reliability.

- **PromptLoader.java**
  - **Description**: A utility class for loading prompt text from raw resource files. It reads files from the Android resources directory and returns their contents as a string, with a fallback prompt if loading fails.
  - **Key Features**:
    - Loads text from raw resource files using `BufferedReader`.
    - Trims trailing newlines and handles IO exceptions gracefully.

- **Planner.java**
  - **Description**: Generates action plans for the Temi robot using the DeepSeek-R1 LLM. It constructs a prompt based on action history and current location, queries the LLM, and parses the response into a list of `TemiAction` objects (e.g., `MoveAction`, `SpeakActionWithoutResponse`, `ConversationAction`). The class includes logic to remove reasoning text from LLM outputs to extract clean JSON plans.
  - **Key Features**:
    - Builds planning prompts incorporating action history and location context.
    - Queries the LLM via `LlmConnector` and processes responses to extract JSON action arrays.
    - Supports action types (`move`, `speak`) with conditional logic for response handling.
    - Includes robust error handling and logging for JSON parsing issues.

### Package: `edu.uky.ai.roguetemi.actions`

- **ActionCompletionListener.java**
  - **Description**: An interface defining a callback for action completion. It allows actions to notify listeners of their status, including success/failure and optional result data.
  - **Key Features**:
    - Defines a single method, `onActionCompleted`, with parameters for action name, success status, and result string.

- **MoveAction.java**
  - **Description**: Implements a `TemiAction` for navigating the Temi robot to a specified location. It listens for navigation status updates and handles completion or failure (e.g., abort by user or obstruction), updating the robot’s current location on success.
  - **Key Features**:
    - Uses the Temi SDK’s `goTo` method and `OnGoToLocationStatusChangedListener` for navigation.
    - Handles navigation retries by adjusting the robot’s position (e.g., turning 90 degrees) if stuck.
    - Notifies the completion listener with the result or error message.

- **TemiAction.java**
  - **Description**: An interface defining the contract for actions the Temi robot can execute. It specifies methods for retrieving the action name, executing the action, and optionally returning a result.
  - **Key Features**:
    - Provides a default `getResult` method returning `null` for actions without results.
    - Ensures all actions have a consistent execution pattern with completion callbacks.

- **SpeakActionWithoutResponse.java**
  - **Description**: Implements a `TemiAction` for making the Temi robot speak a message without waiting for a user response. It uses the Temi SDK’s TTS functionality and notifies the completion listener when the speech is finished.
  - **Key Features**:
    - Creates and plays a `TtsRequest` with the specified message.
    - Listens for TTS status updates to confirm completion.
    - Includes logging for debugging and error handling.

- **ActionManager.java**
  - **Description**: Manages the execution of `TemiAction` objects in a sequential manner using a single-threaded executor. It ensures actions are executed one at a time, with completion callbacks posted to the main thread, and provides methods to check the current action and shut down the executor.
  - **Key Features**:
    - Uses `ExecutorService` for background execution and `Handler` for main-thread callbacks.
    - Maintains a reference to the current action to prevent race conditions.
    - Includes robust shutdown logic with timeout handling.

- **ConversationAction.java**
  - **Description**: Implements a `TemiAction` for handling interactive conversations with users. It uses the LLaMA 3 LLM (via `LlmConnector`) to generate responses, supports multi-turn dialogues, and ends conversations gracefully using the `conversation_over` tool. The class manages user input timeouts and builds a conversation log for reporting.
  - **Key Features**:
    - Initiates conversations with either a Temi message or user input, using `temi.askQuestion` for speech.
    - Queries the LLM for responses, handling tool calls (e.g., `conversation_over`) and content outputs.
    - Manages conversation turns with a maximum limit and timeout for user responses.
    - Constructs a detailed conversation log from the LLM history for debugging and reporting.

## Example Execution Flow

A typical "day in the life" of RogueTemi unfolds as follows:

1. **Initialization**: The robot starts in the `HomeBase` state, on its charging dock.
2. **Shift Start**: At 8 AM on a weekday, the `checkAndMoveTemi` timer detects it’s time to work. The robot transitions to `MovingToEntrance` and navigates to the `WORKING_LOCATION`.
3. **On Duty**: Upon arrival, it transitions to the `Detecting` state, waiting for events.
4. **User Interaction**: A person stands in front of the robot for 1.5 seconds, triggering a transition to `LlmControl`. The robot says, "Hello! How can I help you?" and uses the `ConversationAction` to handle dialogue.
5. **LLM-Powered Task**: The person asks, "Can you tell me where the cafeteria is?" The `Planner` generates a plan: `[{"type": "speak", "message": "Of course. Follow me."}, {"type": "move", "destination": "cafeteria"}]`. The robot executes these actions.
6. **Task Completion**: After arriving, the robot says, "We have arrived." The action queue empties, and the robot transitions back to `MovingToEntrance`, then `Detecting`.
7. **Scheduled Patrol**: At the top of the hour, the robot transitions to `Patrolling`, completes its route, and returns to `Detecting`.
8. **End of Day**: At 5 PM, the `checkAndMoveTemi` logic triggers a transition to `MovingToHome`. The robot returns to its charging dock, entering `HomeBase`.
