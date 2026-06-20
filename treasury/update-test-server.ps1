# Copy the plugin JAR to the server
scp "C:\Workspace\server\plugins\Treasury-2.0.0-SNAPSHOT.jar" "srv:/srv/minecraft/dc-test-server/plugins"

# Restart the Kubernetes deployment via SSH
ssh srv "kubectl scale deploy dc-test-server -n minecraft --replicas=0"
ssh srv "kubectl wait --for=delete pod -l app=dc-test-server -n minecraft --timeout=120s"
ssh srv "kubectl scale deploy dc-test-server -n minecraft --replicas=1"