#!/bin/bash
# =============================================================================
# init_db.sh — Post-bootstrap initialization script
#
# Patroni calls this script exactly once, on the primary node, immediately
# after the cluster is first initialized (initdb + bootstrap.dcs applied).
# It is NOT called on replicas or on subsequent startups.
#
# Responsibilities:
#   1. Create the 'statcentral' application database
#   2. Create the 'appuser' application role
#   3. Apply schema.sql (tables, indexes, sample data)
#   4. Grant privileges to appuser
# =============================================================================
set -e

echo "[init_db] Creating statcentral database and appuser..."

# Connect via the Unix socket (no network needed — we're on the same host as postgres)
PGPASSWORD=postgres psql -h /var/run/postgresql -U postgres <<-EOSQL
    CREATE DATABASE statcentral;
    CREATE USER appuser WITH PASSWORD 'apppassword';
    GRANT ALL PRIVILEGES ON DATABASE statcentral TO appuser;
EOSQL

echo "[init_db] Applying schema.sql..."

# schema.sql is mounted into the container via the docker-compose volume binding
PGPASSWORD=postgres psql -h /var/run/postgresql -U postgres -d statcentral \
    -f /docker-entrypoint-initdb.d/schema.sql

echo "[init_db] Granting table and sequence privileges to appuser..."

PGPASSWORD=postgres psql -h /var/run/postgresql -U postgres -d statcentral <<-EOSQL
    GRANT ALL ON SCHEMA public TO appuser;
    GRANT ALL ON ALL TABLES IN SCHEMA public TO appuser;
    GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO appuser;
EOSQL

echo "[init_db] Done."