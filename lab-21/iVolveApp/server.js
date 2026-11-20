const express = require('express');

const app = express();
const PORT = process.env.PORT || 3000;

app.get('/', (req, res) => {
    res.send('Hello from iVolveApp! Deployed using Jenkins CI/CD Pipeline.');
});

app.get('/health', (req, res) => {
    res.status(200).json({
        status: 'healthy',
        application: 'iVolveApp'
    });
});

app.listen(PORT, '0.0.0.0', () => {
    console.log(`iVolveApp is running on port ${PORT}`);
});
