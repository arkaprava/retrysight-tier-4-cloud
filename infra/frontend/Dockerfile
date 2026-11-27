FROM node:20-alpine AS build

WORKDIR /app

COPY ui/manager-dashboard/package.json ui/manager-dashboard/package-lock.json* ./
RUN npm ci

COPY ui/manager-dashboard .
RUN npm run build

FROM nginx:1.27-alpine

ENV MANAGER_PORT=28477
ENV MANAGER_HOST=manager

COPY infra/frontend/nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
