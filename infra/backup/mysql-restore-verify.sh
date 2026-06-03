#!/bin/sh
set -eu

BACKUP_FILE="${1:?backup file required}"
RESTORE_DB="${RESTORE_DB:-jagdisu_restore_verify}"
DB_HOST="${DB_HOST:-mysql}"
DB_PORT="${DB_PORT:-3306}"
DB_USERNAME="${DB_USERNAME:-root}"

sha256sum -c "${BACKUP_FILE}.sha256"
mysql -h "${DB_HOST}" -P "${DB_PORT}" -u "${DB_USERNAME}" -p"${DB_PASSWORD}" -e "DROP DATABASE IF EXISTS ${RESTORE_DB}; CREATE DATABASE ${RESTORE_DB};"
gzip -dc "${BACKUP_FILE}" | mysql -h "${DB_HOST}" -P "${DB_PORT}" -u "${DB_USERNAME}" -p"${DB_PASSWORD}" "${RESTORE_DB}"
mysql -h "${DB_HOST}" -P "${DB_PORT}" -u "${DB_USERNAME}" -p"${DB_PASSWORD}" -e "SELECT COUNT(*) AS users_count FROM ${RESTORE_DB}.users;"
mysql -h "${DB_HOST}" -P "${DB_PORT}" -u "${DB_USERNAME}" -p"${DB_PASSWORD}" -e "DROP DATABASE ${RESTORE_DB};"
