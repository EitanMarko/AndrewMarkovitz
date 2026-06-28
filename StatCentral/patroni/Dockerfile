FROM postgres:17

# Install Python and Patroni with etcd3 support.
# psycopg2-binary provides the PostgreSQL adapter Patroni uses internally.
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        python3 python3-pip python3-setuptools curl && \
    pip3 install --break-system-packages patroni[etcd3] psycopg2-binary && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Shared Patroni configuration. Node-specific values (name, connect_address)
# are injected at runtime via PATRONI_* environment variables set in docker-compose.yml.
COPY patroni.yml /etc/patroni.yml

# Post-bootstrap script: runs once on the primary after the cluster is first
# initialized. Creates the statcentral database, appuser role, and applies the schema.
COPY init_db.sh /scripts/init_db.sh
RUN sed -i 's/\r//' /scripts/init_db.sh && chmod +x /scripts/init_db.sh

# 5432 = PostgreSQL, 8008 = Patroni REST API (used by HAProxy health checks)
EXPOSE 5432 8008

# initdb refuses to run as root — switch to the postgres user that the
# postgres:17 base image already defines.
USER postgres
CMD ["patroni", "/etc/patroni.yml"]