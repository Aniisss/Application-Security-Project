const express = require('express');
const app = express();
const port = 3000;

// Middleware to parse JSON bodies (essential for API requests)
app.use(express.json());

// Basic Root Route
app.get('/', (req, res) => {
  res.send('Hello World! Express is running.');
});

// Start the Server
app.listen(port, () => {
  console.log(`Server running at http://localhost:${port}`);
});