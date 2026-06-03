FROM node:20-alpine
WORKDIR /app
COPY package.json index.js ./
RUN npm install && npm cache clean --force
EXPOSE 3000
CMD ["node", "index.js"]
