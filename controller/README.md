# Intercept Controller

A JavaFX application that uses PAL intercept callbacks to control (throttle, pause, step-through)
remote interceptable applications.

The controller runs as a PAL peer named `controller`. Intercepts are registered externally via
`pal intercept apply`, pointing callbacks to this peer. When intercept callbacks arrive, the UI
dynamically spawns a control panel for each unique peer.

## Prerequisites

* PAL installed and on your `PATH` ([installation guide](https://quasientio.github.io/pal/getting-started/))
* etcd running (PAL Directory):
    ```bash
    ../infra/start.sh etcd
    ```
* Optionally source the env scaffolding so you don't have to pass `-d localhost:2379` to every command:
    ```bash
    source ../.env.pal     # uncomment PAL_DIRECTORY first if needed
    ```

## Build

```bash
./gradlew shadowJar
```

## Run

### 1. Start the controller

```bash
pal run -d localhost:2379 \
  -n controller \
  --zmq-rpc auto \
  -cp build/libs/controller-all.jar \
  io.quasient.pal.pocs.controller.ControllerApp
```

A JavaFX window will open, waiting for intercept callbacks.

### 2. Apply intercepts

In another terminal, from this `controller/` directory:

```bash
pal intercept apply -d localhost:2379 controller-bundle.yaml
```

Modify `controller-bundle.yaml` to target different classes/methods as needed.

### 3. Run an interceptable application

```bash
pal run -d localhost:2379 \
  -n my-app \
  --interceptable \
  -cp <classpath> \
  <MainClass>
```

A new tab will appear in the controller window for the peer. Use the controls to:

- **Throttle**: Slow down execution by a configurable delay (ms)
- **Pause**: Suspend execution (toggle to resume)
- **Step**: Single-step through intercepted calls (click "Next" to advance)
- **Print**: Show intercepted method calls in the message log

### Multiple peers

Launch additional interceptable applications — each will get its own tab in the controller.

## Embedding as a component

`ControllerPane` is a reusable `BorderPane` that can be added to any JavaFX scene:

```java
ControllerPane controllerPane = new ControllerPane();
controllerPane.setPalDirectory(palDirectory); // optional, for peer name resolution
parentLayout.getChildren().add(controllerPane);
```

## Cleanup

```bash
../infra/stop.sh etcd
```
