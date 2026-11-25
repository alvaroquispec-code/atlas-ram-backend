# Despliegue de Atlas RAM en el EC2. Lo ejecuta AWS Systems Manager como root (documento AWS-RunShellScript);
# el workflow .github/workflows/deploy.yml define antes SHA, BUCKET y AWS_REGION.
#
# Pasos: 1) respaldo de la base  2) descarga del jar del commit  3) cambio del jar  4) prueba de salud
#        5) si la prueba falla, vuelve al jar anterior y termina con error.
# Las migraciones de Flyway no se deshacen: deben ser compatibles hacia atrás (solo agregan tablas o columnas),
# así el jar anterior sigue funcionando con la base migrada.
set -eu
set -o pipefail 2>/dev/null || true

HOME_DIR="${DEPLOY_HOME:-/home/ec2-user}"
APP="$HOME_DIR/app.jar"
PREV="$HOME_DIR/app-previo.jar"
NEW="$HOME_DIR/releases/app-$SHA.jar"
BACKUPS="$HOME_DIR/backups"
SERVICE=atlasram
ENV_FILE="${ENV_FILE:-/etc/atlasram.env}"
HEALTH_URL=http://localhost:8080/api/v1/districts

log() { echo "[$(date -u +%H:%M:%S)] $*"; }

mkdir -p "$HOME_DIR/releases" "$BACKUPS"

# 1) Respaldo de la base con las credenciales del servicio, sin mostrarlas.
log "Respaldo de la base"
(
  set -a; . "$ENV_FILE"; set +a
  export PGPASSWORD="$SPRING_DATASOURCE_PASSWORD"
  pg_dump "postgresql://${SPRING_DATASOURCE_USERNAME}@${SPRING_DATASOURCE_URL#jdbc:postgresql://}" \
    -Fc -f "$BACKUPS/antes-de-$SHA.dump"
)
ls -1t "$BACKUPS"/*.dump | tail -n +6 | xargs -r rm -f   # conserva los 5 más recientes
log "Respaldo listo: antes-de-$SHA.dump"

# 2) Jar del commit desde S3 (el rol de la instancia solo puede leer releases/).
log "Descargando releases/$SHA.jar"
aws s3 cp "s3://$BUCKET/releases/$SHA.jar" "$NEW" --region "$AWS_REGION" --only-show-errors
chown ec2-user:ec2-user "$NEW" "$HOME_DIR/releases" "$BACKUPS"

# 3) Cambio del jar.
cp -p "$APP" "$PREV"
systemctl stop "$SERVICE"
cp "$NEW" "$APP"
chown ec2-user:ec2-user "$APP"
systemctl start "$SERVICE"
log "Servicio reiniciado con $SHA"

# 4) Prueba de salud: hasta 3 minutos (Flyway puede estar migrando).
i=0
while [ "$i" -lt 36 ]; do
  if curl -fsS -o /dev/null "$HEALTH_URL"; then
    log "Salud OK"
    journalctl -u "$SERVICE" --since "-5 min" --no-pager | grep -E "Successfully applied|Schema .* is up to date|Started " || true
    ls -1t "$HOME_DIR"/releases/app-*.jar | tail -n +4 | xargs -r rm -f   # conserva 3 versiones
    exit 0
  fi
  i=$((i + 1))
  sleep 5
done

# 5) Vuelta atrás.
log "La prueba de salud falló: vuelvo al jar anterior"
journalctl -u "$SERVICE" --since "-5 min" --no-pager | tail -n 60
systemctl stop "$SERVICE"
cp "$PREV" "$APP"
chown ec2-user:ec2-user "$APP"
systemctl start "$SERVICE"
exit 1
