#!/bin/bash

echo "Starting database setup..."

# Source the environment variables
source "$(dirname "$0")/../set_env.sh"

# Extract database details from environment variables
DB_URL=$SPRING_DATASOURCE_URL
DB_USER=$SPRING_DATASOURCE_USERNAME
DB_PASSWORD=$SPRING_DATASOURCE_PASSWORD

# Extract host, port, and database name from the URL using grep with Perl-compatible regex
DB_HOST=$(echo "$DB_URL" | grep -oP '(?<=//)[^:]+')
DB_PORT=$(echo "$DB_URL" | grep -oP '(?<=:)[0-9]+(?=/)')
DB_NAME=cam_db
# $(echo "$DB_URL" | grep -oP '(?<=/)[^/?]+')

if [ -z "$DB_HOST" ] || [ -z "$DB_PORT" ] || [ -z "$DB_NAME" ]; then
    echo "Error: Could not parse database connection details from SPRING_DATASOURCE_URL."
    echo "Please ensure SPRING_DATASOURCE_URL is set correctly in set_env.sh (e.g., jdbc:postgresql://localhost:5432/cam_db)"
    exit 1
fi

echo "Database Host: $DB_HOST"
echo "Database Port: $DB_PORT"
echo "Database Name: $DB_NAME"
echo "Database User: $DB_USER"

# Check if psql is installed
if ! command -v psql &> /dev/null
then
    echo "Error: psql command not found. Please install PostgreSQL client tools."
    exit 1
fi

# Set PGPASSWORD for non-interactive psql commands
export PGPASSWORD=$DB_PASSWORD

# Check if the database exists
# Connect to the default 'postgres' database to check for existence
DB_EXISTS=$(psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -tAc "SELECT 1 FROM pg_database WHERE datname='$DB_NAME'" postgres 2>/dev/null)

if [ "$DB_EXISTS" = "1" ]; then
    echo "Database '$DB_NAME' already exists."
    echo "Dropping and recreating public schema to ensure a clean state..."
    # Drop all tables in the public schema and recreate it
    psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
    if [ $? -ne 0 ]; then
        echo "Error: Failed to drop and recreate public schema in database '$DB_NAME'."
        unset PGPASSWORD
        exit 1
    fi
    echo "Public schema in '$DB_NAME' reset successfully."
else
    echo "Database '$DB_NAME' does not exist. Creating it..."
    # Connect to the default 'postgres' database to create the new database
    psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres -c "CREATE DATABASE \"$DB_NAME\";"
    if [ $? -ne 0 ]; then
        echo "Error: Failed to create database '$DB_NAME'."
        unset PGPASSWORD
        exit 1
    fi
    echo "Database '$DB_NAME' created successfully."
fi

echo "Applying schema from cam-core/src/main/resources/schema.sql to '$DB_NAME'..."
# Apply the schema to the target database
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f cam-core/src/main/resources/schema.sql
if [ $? -ne 0 ]; then
    echo "Error: Failed to apply schema to database '$DB_NAME'."
    unset PGPASSWORD
    exit 1
fi

echo "Applying sample data from setup/01_sample_agents.sql to '$DB_NAME'..."
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f setup/01_sample_agents.sql
if [ $? -ne 0 ]; then
    echo "Error: Failed to apply sample agent data to database '$DB_NAME'."
    unset PGPASSWORD
    exit 1
fi

echo "Applying workflow definitions from YAML files to '$DB_NAME'..."

WORKFLOW_DIR="cam-app/src/main/resources/workflows"

if ls "$WORKFLOW_DIR"/*.yaml &>/dev/null 2>&1 && command -v python3 &>/dev/null; then
    echo "Converting YAML workflow definitions to JSONB..."
    python3 - "$WORKFLOW_DIR"/*.yaml << 'PYEOF' | psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME"
import json, sys, yaml
from pathlib import Path

def convert(data):
    start = data.get("start")
    steps = data.get("steps", {})
    objs = {}
    for sid, s in steps.items():
        t = s.get("type", "standard")
        st = {"standard":"STANDARD","join":"JOIN","loop":"LOOP_GATE",
              "switch":"DECISION_GATE","discussion":"DISCUSSION_PANEL"}.get(t.lower(),"STANDARD")
        obj = {"id":sid,"type":st,"role":s.get("role",""),
               "output_topic":sid+"-outputs","status":"PENDING"}
        nxt = s.get("next")
        if nxt is not None: obj["next_step"] = nxt
        if st == "DISCUSSION_PANEL":
            if s.get("pool"): obj["expert_pool"] = s["pool"]
            if s.get("moderator"): obj["moderator_role"] = s["moderator"]
            if s.get("moderator_interval") is not None: obj["moderator_interval"] = s["moderator_interval"]
            if s.get("max_rounds") is not None: obj["max_total_steps"] = s["max_rounds"]
        inm = s.get("in")
        if inm:
            obj["input_mappings"] = [{"source":k,"target":v} for k,v in inm.items()]
        out = s.get("out")
        if out:
            def norm(v):
                if isinstance(v,str): return "array" if v.endswith("[]") else v
                if isinstance(v,dict): return {k2:norm(v2) for k2,v2 in v.items()}
                return v
            obj["output_schema"] = {k:norm(v) for k,v in out.items()}
        objs[sid] = obj
    return {"start_step":start,"current_steps":[start],"steps":objs}

for path in sys.argv[1:]:
    with open(path) as f:
        d = yaml.safe_load(f)
    name = d.get("name", Path(path).stem)
    desc = d.get("description", "")
    j = json.dumps(convert(d), ensure_ascii=False).replace("'","''")
    print(f"INSERT INTO workflow_definitions (id, description, routing_slip_template) VALUES ('{name.replace(chr(39),chr(39)+chr(39))}', '{desc.replace(chr(39),chr(39)+chr(39))}', '{j}'::jsonb) ON CONFLICT (id) DO UPDATE SET description = EXCLUDED.description, routing_slip_template = EXCLUDED.routing_slip_template;")
PYEOF
    if [ $? -ne 0 ]; then
        echo "Warning: YAML import failed, falling back to SQL file..."
        psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f setup/02_sample_workflows.sql
    fi
elif [ -f "setup/02_sample_workflows.sql" ]; then
    echo "YAML or Python not available, falling back to static SQL..."
    psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f setup/02_sample_workflows.sql
    if [ $? -ne 0 ]; then
        echo "Error: Failed to apply sample workflow data."
        unset PGPASSWORD
        exit 1
    fi
fi

echo "Database setup complete for '$DB_NAME'."

# Unset PGPASSWORD for security
unset PGPASSWORD
