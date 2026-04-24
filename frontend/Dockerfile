# Build stage
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
# Mainland-friendly npm defaults to reduce "stuck at npm ci".
# Can be overridden at build time via --build-arg NPM_REGISTRY=...
ARG NPM_REGISTRY=https://registry.npmmirror.com
RUN npm config set registry "${NPM_REGISTRY}" \
    && npm config set fetch-retries 5 \
    && npm config set fetch-retry-mintimeout 20000 \
    && npm config set fetch-retry-maxtimeout 120000 \
    && npm config set fund false \
    && npm config set audit false \
    && npm ci --prefer-online --no-audit --no-fund
COPY . .
RUN npm run build

# Serve with Nginx (reverse proxy /api → backend in docker-compose network)
FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
