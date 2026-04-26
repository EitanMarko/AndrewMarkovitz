# COM3810 Assignment

This repository contains the assignment files for COM3810.

**Write-up:** [Google Doc](https://docs.google.com/document/d/1RQpZQmtAlLVfS6dMn5vzU3Bk2ftvIy8_SuJBgGB_Isw/edit?usp=sharing)

---

## Table of Contents

- [Architecture](#architecture)
- [Step 1 — Install Docker](#step-1--install-docker)
  - [Docker Desktop (Windows or macOS — easiest)](#docker-desktop-windows-or-macos--easiest)
  - [Windows without Docker Desktop (WSL2)](#windows-without-docker-desktop-wsl2)
  - [macOS without Docker Desktop (Colima)](#macos-without-docker-desktop-colima)
  - [Linux](#linux)
- [Step 2 — Start the cluster](#step-2--start-the-cluster)
- [Step 3 — Run the demo](#step-3--run-the-demo)
- [Step 4 — Run the tests](#step-4--run-the-tests)
  - [Full cluster walkthrough (second demo)](#full-cluster-walkthrough-second-demo)
  - [Cluster-resilience tests](#cluster-resilience-tests-no-docker-stack-required)
  - [Database/failover tests](#databasefailover-tests-docker-stack-must-be-running)
- [Stopping and resetting](#stopping-and-resetting)
- [Useful commands](#useful-commands)

---

## Architecture

```
Java microservices (WorkerServers)
        ↓  JDBC on port 6432
    PgBouncer  — connection pooler
        statcentral_write → HAProxy:5000 → Patroni primary
        statcentral_read  → HAProxy:5001 → Patroni replicas
        ↓
    HAProxy  — primary/replica router  (stats: http://localhost:7000)
        ↓
    Patroni1 / Patroni2 / Patroni3  — HA PostgreSQL (etcd for leader election)
        ↓
    etcd  — distributed configuration store
```

---

## Step 1 — Install Docker

Pick the path that matches your OS. Once Docker is installed and running, the cluster commands in Steps 2–4 are identical on every platform.

### Docker Desktop (Windows or macOS — easiest)

Install [Docker Desktop](https://www.docker.com/products/docker-desktop) and start it. That's all — skip to [Step 2](#step-2--start-the-cluster).

---

### Windows without Docker Desktop (WSL2)

WSL2 (Windows Subsystem for Linux) lets you run a full Linux environment — including Docker — directly on Windows. All `docker`, `mvn`, and `java` commands must be run from inside the WSL2 shell.

**1. Enable WSL2 and install Ubuntu**

Open PowerShell as Administrator and run:
```powershell
wsl --install
```
Restart when prompted. Ubuntu will be installed automatically. Open it from the Start Menu to finish setup.

**2. Install Docker Engine inside Ubuntu**
```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
newgrp docker
sudo service docker start
```

**3. Install Java 21 and Maven**
```bash
sudo apt-get install -y openjdk-21-jdk maven
```

**4. Copy the project to the native Linux filesystem**

> **Important:** Do not run the project from `/mnt/c/`. The WSL2 filesystem bridge makes file I/O slow enough that Java's class-loading on first startup exceeds request timeouts, causing the demo and all tests to fail. Always work from the native Linux filesystem.

```bash
cp -r /mnt/c/Users/<your-username>/path/to/com3810/StatCentral ~/statcentral
cd ~/statcentral
```

Now skip to [Step 2](#step-2--start-the-cluster).

---

### macOS without Docker Desktop (Colima)

[Colima](https://github.com/abiosoft/colima) is a lightweight free alternative to Docker Desktop on macOS.

```bash
brew install colima docker docker-compose
colima start
```

That's it — `docker` and `docker compose` are now available in your terminal. Skip to [Step 2](#step-2--start-the-cluster).

---

### Linux

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
newgrp docker
```

---

## Step 2 — Start the cluster

All commands run from the `StatCentral/` project directory.

**First run** (builds the Patroni image):
```bash
docker compose up --build -d
```

**Subsequent runs:**
```bash
docker compose up -d
```

**Verify all six containers are up:**
```bash
docker compose ps
```

Startup takes about 2 minutes:
1. etcd starts (~10s)
2. patroni1 bootstraps, creates the `statcentral` database, and applies the schema (~30s)
3. patroni2 and patroni3 clone patroni1's data in the background (~60–90s)

The HAProxy stats dashboard at **http://localhost:7000** shows live cluster health. Once all three Patroni nodes are green, the cluster is fully ready.

To connect to the database directly (e.g. from IntelliJ or psql):

| Field | Value |
|---|---|
| Host | `localhost` |
| Port | `5000` |
| Database | `statcentral` |
| User | `postgres` |
| Password | `postgres` |

---

## Step 3 — Run the demo

From the `StatCentral/` directory:

```bash
mvn compile dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp "target/classes:$(cat cp.txt)" cluster.MultiProcessDemo
```

> **Windows CMD/PowerShell users:** use a semicolon instead of a colon in the `-cp` argument. WSL2 users use the colon form above.

The demo starts a 4-node Java cluster, populates the database, then pauses for inspection. Press **Enter** to continue cleanup.

---

## Step 4 — Run the tests

> **Note:** `mvn test` must be run from inside the same environment where Docker is running (e.g. the WSL2 shell on Windows), because some tests invoke `docker exec`/`docker stop`/`docker start` as subprocesses.

### Full cluster walkthrough (second demo)

Exercises every API method end-to-end across a live cluster.

```bash
mvn test -Dtest=FullInterfaceTest#testAllMethods
```

### Cluster-resilience tests 
```bash
mvn test -Dtest=FullInterfaceTest#killMultipleFollowers
mvn test -Dtest=FullInterfaceTest#killOneLeader5Peers
mvn test -Dtest=FullInterfaceTest#killGateway
```

### Database/failover tests 
```bash
mvn test -Dtest=FullInterfaceTest#testPrimaryFailover
mvn test -Dtest=FullInterfaceTest#testWipeDatabase
```

---

## Stopping and resetting

| Action | Command |
|---|---|
| Stop (keep data) | `docker compose down` |
| Stop and wipe all data | `docker compose down -v` |
| View logs | `docker compose logs -f` |
| Full reset | `docker compose down -v && docker compose up --build -d` |

---

## Useful commands

| Task | Command |
|---|---|
| Check which node is primary | `docker exec statcentral-patroni1-1 curl -sf http://localhost:8008/primary && echo primary` |
| Connect to the database via psql | `docker exec -it statcentral-patroni1-1 psql -U postgres -d statcentral` |
| Live cluster health dashboard | Open `http://localhost:7000` in a browser |
| View logs for one container | `docker logs -f statcentral-patroni1-1` |