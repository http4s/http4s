#!/usr/bin/env node

const http = require('http');

function sleep(ms) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
} 

http.createServer(async (req, res) => {
  // CORS stuff, needed for testing browser-based clients
  res.setHeader('Access-Control-Allow-Origin', '*');
	res.setHeader('Access-Control-Request-Method', '*');
	res.setHeader('Access-Control-Allow-Methods', '*');
  res.setHeader('Access-Control-Allow-Headers', '*');
  res.setHeader('Access-Control-Expose-Headers', '*');

  res.setHeader('Content-Type', 'text/plain;charset=utf-8');

  if (req.method === 'OPTIONS')
    res.end();
  else if (req.method === 'GET') {
    switch (req.url) {
      case '/simple':
        res.end('simple path');
        break;
      case '/chunked':
        for (const c of 'chunk') {
          await sleep(200);
          res.write(c);
        }
        res.end();
        break;
      case '/delayed':
        await sleep(1000);
        res.end('delayed path')
        break;
      case '/no-content':
        res.statusCode = 204;
        res.end();
        break;
      case '/not-found':
        res.statusCode = 404;
        res.end('not found');
        break;
      case '/empty-not-found':
        res.statusCode = 404;
        res.end();
        break;
      case '/internal-server-error':
        res.statusCode = 500;
        res.end();
        break;
      default:
        res.statusCode = 404;
        res.end();
    }
  } else if (req.method === 'POST') {
    req.on('data', (chunk) => {
      res.write(chunk);
    }).on('end', () => {
      res.end();
    });
  } else {
    res.statusCode = 404;
    res.end();
  }
}).listen(8888);
