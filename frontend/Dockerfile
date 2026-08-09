FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
LABEL org.opencontainers.image.authors="Yashwardhan Verma <yashwardhanverma108@gmail.com>" \
      org.opencontainers.image.source="https://github.com/YashwardhanV/SignalDock"
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
