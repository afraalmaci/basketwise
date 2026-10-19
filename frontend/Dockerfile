# Build stage
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Optional CARTO basemaps key; Vite bakes VITE_* variables into the bundle at build time.
ARG VITE_CARTO_API_KEY=""
ENV VITE_CARTO_API_KEY=$VITE_CARTO_API_KEY
RUN npm run build

# Runtime stage: static files + reverse proxy for /api
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
