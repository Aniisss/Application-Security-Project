const express = require('express');
const path = require('path');
const app = express();
require('dotenv').config();

// 2. Use variables from .env (with fallbacks if they are missing)
const PORT = process.env.PORT || 3000;
const HOST = process.env.HOST || 'localhost';

// Serve static files from the 'public' directory
app.use(express.static(path.join(__dirname, '../public')));

// Middleware to parse JSON bodies (essential for API requests)
app.use(express.json());

// Basic Root Route
app.get('/', (req, res) => {
  res.send('Hello World! Express is running.');
});

// Start the Server
app.listen(PORT, () => {
  console.log(`Server running at http://${HOST}:${PORT}`);
});