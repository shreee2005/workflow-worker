# C:\dev\workflow-worker\docker\Dockerfile
FROM node:20

WORKDIR /usr/src/app

# Install dependencies
COPY package*.json ./
RUN npm install

# Copy the rest of the code
COPY . .

# Expose port if needed (example)
EXPOSE 3000

# Start the worker
CMD ["node", "index.js"]
