# CI/CD con Jenkins + Docker + AWS

**Spring Boot · Vue.js · EC2 · RDS · S3**

Guía completa de despliegue automatizado con contenedores.

> Ejemplo de referencia: *Congress Management System*

---

## Arquitectura General

Este es el flujo completo desde que haces `git push` hasta que el cambio está en producción:

```
DEV MACHINE                              AWS
──────────                               ───

git push backend ──► GitHub ──► Webhook ──► Jenkins (EC2 t2.micro)
                                                  │
                                    ┌─────────────┴─────────────┐
                                    │ Pipeline Back              │
                                    │ 1. mvn test                │
                                    │ 2. docker build            │
                                    │ 3. docker run  ──► contenedor backend :8081
                                    │ 4. nginx reload──► contenedor nginx   :80
                                    └─────────────────────────────┘
                                                                    │
git push frontend ─► GitHub ──► Webhook ──► Jenkins                │
                                    │                               │
                      ┌─────────────┴─────────────┐                ▼
                      │ Pipeline Front              │        RDS MySQL
                      │ 1. npm ci                   │     (congress_system_db)
                      │ 2. npm test                  │
                      │ 3. npm build                 │
                      │ 4. aws s3 sync               │──► S3 Bucket (Vue SPA)
                      └─────────────────────────────┘
```

### Un solo Jenkins — dos pipelines independientes

- Jenkins corre en el EC2 directamente (no en Docker) porque necesita acceso al socket de Docker para construir y levantar contenedores.
- Cada repositorio tiene su propio `Jenkinsfile` y su propio pipeline.
- Los pipelines no se interfieren entre sí — si corren a la vez, Jenkins pone uno en cola.

---

## FASE 1 — Infraestructura en AWS

### 1.1 RDS MySQL — Base de datos

**1. Crear instancia RDS**

Ve a AWS Console → RDS → Create database:

- Engine: MySQL 8.0
- Template: Free tier
- DB instance identifier: `congress-db`
- Master username: `admin` / Password: (anótala)
- Instance type: `db.t3.micro`
- Storage: 20 GB gp2, sin autoscaling
- Public access: No
- VPC: default

**2. Security Group del RDS**

En el Security Group de RDS agrega una Inbound Rule:

| Campo | Valor |
|---|---|
| Type | MySQL/Aurora |
| Port | 3306 |
| Source | El Security Group del EC2 (referencia directa, no IP) |

> Guarda el endpoint. Forma: `congress-db.xxxxxxxxxx.us-east-1.rds.amazonaws.com`
> Lo necesitas para la variable `DB_URL` del backend.

**3. Crear la base de datos**

Conéctate temporalmente habilitando acceso público, crea la BD y vuelve a desactivarlo:

```sql
mysql -h <RDS_ENDPOINT> -u admin -p

CREATE DATABASE congress_system_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

EXIT;
```

### 1.2 EC2 — Servidor principal

**1. Lanzar instancia EC2**

- AMI: Ubuntu Server 22.04 LTS (64-bit x86)
- Instance type: `t2.micro` (free tier)
- Key pair: crear nuevo → `congress-key.pem`
- Storage: 20 GB gp2

Inbound Rules del Security Group:

| Servicio | Puerto | Origen |
|---|---|---|
| SSH | 22 | Tu IP /32 únicamente |
| HTTP | 80 | 0.0.0.0/0 (Nginx) |
| HTTPS | 443 | 0.0.0.0/0 (futuro) |
| Jenkins | 8080 | Tu IP /32 únicamente |
| Backend dev | 8081 | Tu IP /32 (debug opcional) |

**2. Conectar por SSH**

```bash
chmod 400 congress-key.pem
ssh -i congress-key.pem ubuntu@<EC2_PUBLIC_IP>
```

### 1.3 Optimizaciones obligatorias del EC2

Conéctate por SSH (`ssh -i key.pem ubuntu@<IP>`) y ejecuta estos ajustes para evitar que Jenkins colapse por falta de RAM y para sincronizar la hora:

**A. Crear memoria swap (2GB):**

```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

**B. Ajustar zona horaria (Guatemala):**

```bash
sudo timedatectl set-timezone America/Guatemala
date # Verificar que diga CST
```

**3. Instalar Docker**

```bash
sudo apt update && sudo apt upgrade -y

# Instalar dependencias
sudo apt install -y ca-certificates curl gnupg lsb-release

# Agregar repositorio oficial de Docker
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | \
  sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo "deb [arch=$(dpkg --print-architecture) \
  signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# Verificar
sudo docker --version
sudo docker compose version

#Agregar permisos
sudo usermod -aG docker ubuntu
```

**4. Agregar jenkins al grupo docker** (después de instalar Jenkins)

Esto permite que Jenkins ejecute docker sin `sudo`:

```bash
sudo usermod -aG docker jenkins
sudo systemctl restart jenkins
```

**5. Instalar Jenkins**

Jenkins requiere Java 21 para funcionar correctamente. Ejecuta estos comandos paso a paso para instalar el entorno y el servidor:

```bash
# 1. Instalar Java 21 (requisito indispensable para Jenkins)
sudo apt update
sudo apt install -y fontconfig openjdk-21-jdk

# 1.5 Instalar Maven
sudo apt install -y maven

# 2. Descargar la llave de seguridad oficial actualizada (2026)
sudo wget -O /usr/share/keyrings/jenkins-keyring.asc \
  https://pkg.jenkins.io/debian-stable/jenkins.io-2026.key

# 3. Agregar el repositorio estable de Jenkins
echo "deb [signed-by=/usr/share/keyrings/jenkins-keyring.asc] \
  https://pkg.jenkins.io/debian-stable binary/" | sudo tee \
  /etc/apt/sources.list.d/jenkins.list > /dev/null

# 4. Actualizar repositorios e instalar Jenkins
sudo apt update
sudo apt install -y jenkins

# 5. Arrancar el servicio y habilitarlo para que inicie con el sistema
sudo systemctl start jenkins
sudo systemctl enable jenkins

# ver si esta funcionando
sudo systemctl status jenkins

# 6. Obtener la contraseña maestra inicial
sudo cat /var/lib/jenkins/secrets/initialAdminPassword
```

Abre `http://<EC2_IP>:8080`, pega la contraseña, instala los plugins sugeridos.

**6. Instalar Node.js y AWS CLI** (para el pipeline del frontend)

```bash
# Node.js 20 LTS
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs
node --version

# AWS CLI v2
curl 'https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip' -o awscliv2.zip
sudo apt install -y unzip
unzip awscliv2.zip
sudo ./aws/install
aws --version
```

### 1.4 S3 — Frontend estático

**1. Crear bucket S3**

- Ve a S3 → Create bucket
- Bucket name: `congress-frontend` (único globalmente)
- Región: `us-east-1`
- Desactiva "Block all public access" + confirma el checkbox

**2. Habilitar Static Website Hosting**

Properties → Static website hosting → Enable:

- Index document: `index.html`
- Error document: `index.html` ← importante para Vue Router en modo history

> Guarda la URL del endpoint (la necesitas para CORS del backend).

**3. Bucket Policy — acceso público**

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Sid": "PublicReadGetObject",
    "Effect": "Allow",
    "Principal": "*",
    "Action": "s3:GetObject",
    "Resource": "arn:aws:s3:::congress-frontend/*"
  }]
}
```

**4. Crear usuario IAM para Jenkins (deploy a S3)**

- IAM → Users → Create user: `jenkins-s3-deployer`
- Attach policies: `AmazonS3FullAccess`
- Create Access Key → Application running outside AWS → Descarga el CSV

> Las keys las guardarás como credenciales en Jenkins en la Fase 4.

---

## FASE 2 — Archivos Docker del Backend

Todos estos archivos van en la raíz del repositorio del backend, al mismo nivel que `pom.xml`.

### 2.1 Dockerfile

Usa un build multi-stage: la primera etapa compila, la segunda solo ejecuta. La imagen final es más pequeña y no incluye Maven.

```dockerfile
# ── Etapa 1: Build ───────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# Copiar pom.xml primero para aprovechar cache de dependencias
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copiar el codigo fuente y compilar
COPY src ./src
RUN mvn clean package -DskipTests -B

# ── Etapa 2: Runtime ─────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Crear usuario no-root por seguridad
RUN groupadd -r appgroup && useradd -r -g appgroup appuser

COPY --from=builder /app/target/*.jar app.jar
RUN chown appuser:appgroup app.jar
USER appuser

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 2.2 nginx.conf

Configuración de Nginx que corre en su propio contenedor y hace reverse proxy al backend:

```nginx
events {
  worker_connections 1024;
}

http {
  upstream backend {
    server backend:8081; # 'backend' es el nombre del contenedor
  }

  server {
    listen 80;

    # API del backend
    location /api {
      proxy_pass http://backend;
      proxy_http_version 1.1;
      proxy_set_header Host $host;
      proxy_set_header X-Real-IP $remote_addr;
      proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
      proxy_set_header X-Forwarded-Proto $scheme;
      proxy_read_timeout 300;
      proxy_connect_timeout 300;
      proxy_send_timeout 300;
    }

    # Health check publico
    location /health {
      return 200 'OK';
      add_header Content-Type text/plain;
    }
  }
}
```

### 2.3 docker-compose.yml

Define los dos contenedores (backend + nginx) y cómo se comunican entre sí:

```yaml
version: '3.8'

services:
  backend:
    image: congress-backend:latest # imagen construida por Jenkins
    container_name: congress-backend
    restart: unless-stopped
    env_file:
      - .env.prod # variables de entorno en el servidor
    environment:
      - SPRING_PROFILES_ACTIVE=prod
    expose:
      - '8081' # solo visible dentro de la red Docker
    networks:
      - congress-net
    healthcheck:
      test: ['CMD', 'curl', '-f', 'http://localhost:8081/actuator/health']
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s

  nginx:
    image: nginx:alpine
    container_name: congress-nginx
    restart: unless-stopped
    ports:
      - '80:80' # unico puerto expuesto al exterior
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
    depends_on:
      backend:
        condition: service_healthy
    networks:
      - congress-net

networks:
  congress-net:
    driver: bridge
```

### 2.4 Modificar CongressManagementApplication.java

El `.env` de dotenv solo existe en desarrollo local. En producción las variables llegan por `env_file` del `docker-compose`. El cambio es hacer la carga condicional:

```java
@SpringBootApplication
public class CongressManagementApplication {

    public static void main(String[] args) {
        // Cargar .env solo si existe (desarrollo local)
        // En produccion Docker inyecta las variables directamente
        if (new java.io.File(".env").exists()) {
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            loadEnvVar(dotenv, "JWT_SECRET");
            loadEnvVar(dotenv, "JWT_EXPIRATION");
            loadEnvVar(dotenv, "JWT_REFRESH_EXPIRATION");
            loadEnvVar(dotenv, "DB_URL");
            loadEnvVar(dotenv, "DB_USERNAME");
            loadEnvVar(dotenv, "DB_PASSWORD");
            loadEnvVar(dotenv, "AWS_ACCESS_KEY");
            loadEnvVar(dotenv, "AWS_SECRET_KEY");
            loadEnvVar(dotenv, "AWS_REGION");
            loadEnvVar(dotenv, "AWS_S3_BUCKET_NAME");
            loadEnvVar(dotenv, "MAIL_USERNAME");
            loadEnvVar(dotenv, "MAIL_APP_PASSWORD");
            loadEnvVar(dotenv, "MAIL_FROM_NAME");
            loadEnvVar(dotenv, "APP_FRONTEND_URL");
        }
        SpringApplication.run(CongressManagementApplication.class, args);
    }

    private static void loadEnvVar(Dotenv dotenv, String key) {
        String value = dotenv.get(key);
        if (value != null) System.setProperty(key, value);
    }
}
```

### 2.5 application-prod.properties

Crea `src/main/resources/application-prod.properties`:

```properties
# Puerto del servidor
server.port=8081

# CORS — URL del bucket S3 del frontend
app.cors.allowed-origins=http://congress-frontend.s3-website-us-east-1.amazonaws.com

# Swagger desactivado en produccion
springdoc.api-docs.enabled=false
springdoc.swagger-ui.enabled=false

# Logging reducido
logging.level.com.congreso=INFO
logging.level.org.springframework.security=WARN

# Actuator solo health (para healthcheck de Docker)
management.endpoints.web.exposure.include=health
management.endpoint.health.show-details=never

# Frontend URL para emails de invitacion
app.frontend.url=http://congress-frontend.s3-website-us-east-1.amazonaws.com
```

### 2.6 Jenkinsfile del Backend

Crea el archivo `Jenkinsfile` en la raíz del repositorio del backend:

```groovy
pipeline {
    agent any
    environment {
        IMAGE_NAME = 'congress-backend'
        DEPLOY_DIR = '/opt/congress-backend'
    }
    stages {
        stage('Checkout') {
            steps { checkout scm }
        }
        stage('Test') {
            steps {
                sh 'mvn test -B'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }
        stage('Build Docker Image') {
            steps {
                sh 'docker build -t $IMAGE_NAME:latest -t $IMAGE_NAME:$BUILD_NUMBER .'
            }
        }
        stage('Deploy') {
            steps {
                sh '''
                    # Copiar archivos de configuracion al servidor
                    cp docker-compose.yml $DEPLOY_DIR/docker-compose.yml
                    cp nginx.conf $DEPLOY_DIR/nginx.conf

                    # Levantar/actualizar contenedores
                    cd $DEPLOY_DIR
                    docker compose up -d --no-deps backend
                    docker compose up -d --no-deps nginx

                    # Limpiar imagenes viejas
                    docker image prune -f
                '''
            }
        }
    }
    post {
        failure { echo 'Pipeline del backend fallo' }
        success { echo 'Backend desplegado exitosamente' }
    }
}
```

### 2.7 .dockerignore

Crea `.dockerignore` en la raíz del backend para que el build de Docker sea más rápido:

```
target/
.git/
.idea/
*.md
.env
.env.example
Jenkinsfile
```

---

## FASE 3 — Archivos del Frontend

### 3.1 .env.production

Crea `.env.production` en la raíz del proyecto Vue. Este archivo **sí** va al repositorio (no tiene secretos):

```bash
# URL del backend en produccion (a través de Nginx en el EC2)
VITE_API_BASE_URL=http://<EC2_PUBLIC_IP>/api

# Si configuras un dominio propio:
# VITE_API_BASE_URL=https://api.tu-dominio.com/api
```

### 3.2 Jenkinsfile del Frontend

Crea `Jenkinsfile` en la raíz del repositorio Vue:

```groovy
pipeline {
    agent any
    environment {
        S3_BUCKET = 'congress-frontend'
        AWS_REGION = 'us-east-1'
    }
    stages {
        stage('Checkout') {
            steps { checkout scm }
        }
        stage('Install') {
            steps {
                sh 'npm ci'
            }
        }
        stage('Test') {
            steps {
                sh 'npm run test:unit -- --run'
            }
        }
        stage('Build') {
            steps {
                sh 'npm run build -- --mode production'
            }
        }
        stage('Deploy to S3') {
            steps {
                withCredentials([[
                    $class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: 'aws-s3-credentials'
                ]]) {
                    sh '''
                        # Subir assets con cache larga (1 año)
                        aws s3 sync dist/ s3://$S3_BUCKET \
                          --region $AWS_REGION \
                          --delete \
                          --cache-control 'max-age=31536000,public' \
                          --exclude 'index.html'

                        # index.html sin cache (para que siempre cargue la version nueva)
                        aws s3 cp dist/index.html s3://$S3_BUCKET/index.html \
                          --region $AWS_REGION \
                          --cache-control 'no-cache,no-store,must-revalidate'
                    '''
                }
            }
        }
    }
    post {
        failure { echo 'Pipeline del frontend fallo' }
        success { echo 'Frontend desplegado en S3 exitosamente' }
    }
}
```

---

## FASE 4 — Configuración de Jenkins

### 4.1 Plugins a instalar

Jenkins → Manage Jenkins → Plugins → Available plugins:

- Git plugin
- GitHub plugin
- Pipeline
- AWS Credentials Plugin
- Amazon Web Services SDK :: All
- JUnit Plugin
- Docker Pipeline (para poder usar docker dentro de pipelines)

### 4.2 Credenciales

Jenkins → Manage Jenkins → Credentials → System → Global → Add Credentials:

**1. GitHub — acceso a repositorios privados**

- Kind: Username with password
- Username: `tu-usuario-github`
- Password: Personal Access Token — GitHub → Settings → Developer settings → Tokens (classic)
- Scopes necesarios: `repo` (full)
- ID: `github-credentials`

**2. AWS — para subir el frontend a S3**

- Kind: AWS Credentials
- Access Key ID: (del usuario IAM `jenkins-s3-deployer`)
- Secret Access Key: (del usuario IAM `jenkins-s3-deployer`)
- ID: `aws-s3-credentials` ← este ID debe coincidir exactamente con el Jenkinsfile

### 4.3 Pipeline del Backend

Jenkins → New Item → Nombre: `congress-backend` → Pipeline → OK

**1. General**

- Marca: GitHub project
- Project URL: `https://github.com/tu-usuario/backend-repo`

**2. Build Triggers**

- Marca: GitHub hook trigger for GITScm polling

**3. Pipeline definition**

- Definition: Pipeline script from SCM
- SCM: Git
- Repository URL: `https://github.com/tu-usuario/backend-repo`
- Credentials: `github-credentials`
- Branch Specifier: `*/main`
- Script Path: `Jenkinsfile`

### 4.4 Pipeline del Frontend

Jenkins → New Item → Nombre: `congress-frontend` → Pipeline → OK

Misma configuración que el backend pero apuntando al repositorio del frontend.

### 4.5 GitHub Webhooks

En cada repositorio de GitHub → Settings → Webhooks → Add webhook:

| Campo | Valor |
|---|---|
| Payload URL | `http://<EC2_PUBLIC_IP>:8080/github-webhook/` |
| Content type | `application/json` |
| Events | Just the push event |
| Active | checked |

> **Importante — Puerto 8080 en el Security Group**
> Para que GitHub pueda enviar webhooks, el puerto 8080 del EC2 debe ser accesible desde internet, o al menos desde las IPs de GitHub. Durante pruebas puedes abrirlo a `0.0.0.0/0` temporalmente.
> Lista de IPs de GitHub: `https://api.github.com/meta` (campo `hooks`)

---

## FASE 5 — Preparar el EC2 para el Deploy

### 5.1 Crear directorio de deploy y archivo de variables

Conéctate al EC2 por SSH y ejecuta:

```bash
# Crear directorio donde Jenkins copiará los archivos
sudo mkdir -p /opt/congress-backend
sudo chown jenkins:jenkins /opt/congress-backend

# Crear el archivo de variables de entorno de produccion
sudo nano /opt/congress-backend/.env.prod
```

Contenido del archivo `.env.prod` — con tus valores reales de producción:

```bash
JWT_SECRET=tu_jwt_secret_muy_largo_y_aleatorio_minimo_32_caracteres
JWT_EXPIRATION=3600000
JWT_REFRESH_EXPIRATION=604800000
DB_URL=jdbc:mysql://congress-db.xxxxxxxxx.us-east-1.rds.amazonaws.com:3306/congress_system_db
DB_USERNAME=admin
DB_PASSWORD=tu_password_de_rds
AWS_ACCESS_KEY=AKIAIOSFODNN7EXAMPLE
AWS_SECRET_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
AWS_REGION=us-east-1
AWS_S3_BUCKET_NAME=congress-files
MAIL_USERNAME=tu@gmail.com
MAIL_APP_PASSWORD=xxxx xxxx xxxx xxxx
MAIL_FROM_NAME=Congress Management
APP_FRONTEND_URL=http://congress-frontend.s3-website-us-east-1.amazonaws.com
```

```bash
# Proteger el archivo — solo jenkins puede leerlo
sudo chown jenkins:jenkins /opt/congress-backend/.env.prod
sudo chmod 600 /opt/congress-backend/.env.prod
```

### 5.2 Permisos de Jenkins para Docker

Jenkins necesita correr `docker compose` sin `sudo`. Ya agregaste jenkins al grupo docker en la Fase 1, verifica:

```bash
groups jenkins
# Debe incluir: docker

# Si no aparece docker:
sudo usermod -aG docker jenkins
sudo systemctl restart jenkins

# Dar permisos a Jenkins sobre el directorio
sudo chown -R jenkins:jenkins /opt/congress-backend
```

### 5.3 Primer deploy manual para verificar

Antes de que Jenkins lo haga, prueba el flujo manualmente:

```bash
cd /opt/congress-backend

# Construir imagen (esto lo hará Jenkins, pero probamos manualmente)
docker build -t congress-backend:latest /ruta/al/codigo/backend

# Levantar los contenedores
docker compose up -d

# Verificar que están corriendo
docker compose ps

# Ver logs del backend
docker compose logs -f backend

# Probar que el backend responde
curl http://localhost/api/actuator/health
```

---

## FASE 6 — Primer Despliegue Completo

### 6.1 Orden correcto del primer deploy

1. Verifica que RDS esté disponible y que `congress_system_db` exista.
2. Verifica que `/opt/congress-backend/.env.prod` esté completo y con los valores correctos.
3. Haz un push al repositorio del backend (o usa Build Now en Jenkins).
4. Observa el pipeline en Jenkins — deben pasar los 4 stages: Checkout, Test, Build, Deploy.
5. Verifica los contenedores: `docker compose ps` (deben estar Up).
6. Prueba el backend: `curl http://<EC2_IP>/api/actuator/health`.
7. Haz un push al repositorio del frontend (o Build Now).
8. Abre la URL del bucket S3 — el frontend debe cargar.
9. Prueba el login en el frontend — debe conectarse al backend.

### 6.2 Comandos útiles para debug

```bash
# Ver todos los contenedores
docker compose -f /opt/congress-backend/docker-compose.yml ps

# Logs del backend en tiempo real
docker compose -f /opt/congress-backend/docker-compose.yml logs -f backend

# Logs de Nginx
docker compose -f /opt/congress-backend/docker-compose.yml logs -f nginx

# Reiniciar solo el backend (sin tocar nginx)
docker compose -f /opt/congress-backend/docker-compose.yml restart backend

# Ver imagenes Docker
docker images

# Liberar espacio (imagenes huérfanas)
docker image prune -f

# Ver logs de Jenkins
sudo journalctl -u jenkins -f
```

---

## Resumen del Flujo CI/CD Final

| # | Stage | Qué ocurre |
|---|---|---|
| 1 | git push | Desarrollador hace push a la rama `main` del repo backend o frontend |
| 2 | Webhook | GitHub envía una notificación HTTP POST a Jenkins en el puerto 8080 |
| 3 | Checkout | Jenkins clona el repositorio con el código actualizado |
| 4 | Test | `mvn test` (backend) o `npm run test:unit` (frontend) — si falla, el deploy se detiene |
| 5 | Docker build | Jenkins construye la imagen Docker del backend con el nuevo código |
| 6 | Docker deploy | `docker compose up` reemplaza el contenedor viejo por el nuevo sin downtime |
| 7 | S3 sync | Para el frontend: `dist/` se sincroniza con el bucket S3 (solo archivos cambiados) |
| 8 | Listo | El cambio está en producción. Los usuarios ven la versión nueva en su próxima carga |

## Estructura de archivos final por repositorio

**Backend (raíz del repo):**

```
├── Dockerfile
├── docker-compose.yml
├── nginx.conf
├── Jenkinsfile
├── .dockerignore
├── .env          ← solo local, en .gitignore
└── src/
```

**Frontend (raíz del repo):**

```
├── Jenkinsfile
├── .env.production   ← va al repo, no tiene secretos
└── src/
```

**EC2 `/opt/congress-backend/`:**

```
├── docker-compose.yml   ← copiado por Jenkins en cada deploy
├── nginx.conf           ← copiado por Jenkins en cada deploy
└── .env.prod             ← creado manualmente, NUNCA en el repo
```

## Costos estimados AWS Free Tier (12 meses)

| Servicio | Detalle | Costo |
|---|---|---|
| EC2 t2.micro | 750 horas/mes incluidas | $0 |
| RDS db.t3.micro | 750 horas/mes + 20GB storage | $0 |
| S3 frontend | 5 GB + 20,000 GET + 2,000 PUT | $0 |
| Transferencia | 15 GB salida/mes | $0 |
| **Total primer año** | | **$0** (dentro del free tier) |
| Después del año | | ~$15–25 USD/mes |
