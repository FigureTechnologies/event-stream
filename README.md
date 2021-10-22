# provenance-event-stream 

A coroutine-based library for streaming blocks and events from the Provenance blockchain to interested consumers.

## Listening for blocks

The code in this repository can also be run as a standalone utility for displaying live and historical Provenance 
blocks and events.

### Running the event stream listener outside of a container:

Port `26657` is expected to be open for RPC and web-socket traffic, which will
require port-forwarding to the Provenance Kubernetes cluster.

#### Using `figcli`

This can be accomplished easily using the internal Figure command line tool, `figcli`:

1. If it does not already exist, add a `[port_forward]` entry to you local `figcli`
   configuration file:

   ```
   [port_forward]
   context = "gke_provenance-io-test_us-east1-b_p-io-cluster"
   ```

   This example will use the Provenance test cluster.

2. Create a pod in the cluster to perform port-forwarding on a specific remote host and port:

   ```bash
   $ figcli port-forward 26657:$REMOTE_HOST:26657
   ```
   where `$REMOTE_HOST` is a private IP within the cluster network, e.g. `192.168.xxx.xxx`

3. If successful, you should see the pod listed in the output of `kubectl get pods`:

   ```bash
   $ kubectl get pods
   
   NAME                                READY   STATUS    RESTARTS   AGE
   datadog-agent-29nvm                 1/1     Running   2          26d
   datadog-agent-44rdl                 1/1     Running   1          26d
   datadog-agent-xcj48                 1/1     Running   1          26d
   ...
   datadog-agent-xfkmw                 1/1     Running   1          26d
   figcli-temp-port-forward-fi8dlktx   1/1     Running   0          15m   <<<
   nginx-test                          1/1     Running   0          154d
   ```
   
Traffic on `localhost:26657` will now be forwarded to the Provenance cluster

### 5. Running

The service can be run locally:

```bash
$ make run-local
```

To pass an arbitrary argument string to the service, run `make run-local` with a variable named `ARGS`:

```bash
$ make run-local ARGS="--from=3017000"  # Display blocks starting from 3017000
```

```bash
$ make run-local ARGS="--verbose"  # Only show live blocks with verbose output
```