# Log into your server
ssh githubuser@fov.ee

# Initialize swarm mode
docker swarm init --advertise-addr $(hostname -i)

# Create required network
docker network create --driver overlay portfolio_network

# Create a .env file with your environment variables (or copy it from your local machine)
vi .env

# Deploy the stack for the first time
docker stack deploy -c docker-stack.yml --with-registry-auth portfolio
