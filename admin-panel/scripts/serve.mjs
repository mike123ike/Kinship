import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { dirname, resolve, relative, isAbsolute } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../dist');
const mime = {
  '.json': 'application/json',
  '.html': 'text/html',
  '.css': 'text/css',
  '.mjs': 'text/javascript',
  '.js': 'text/javascript',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
};
const server = createServer(async (request, response) => {
  try {
    if (!['GET', 'HEAD'].includes(request.method)) {
      response.writeHead(405, { Allow: 'GET, HEAD' });
      return response.end();
    }
    const pathname = decodeURIComponent(new URL(request.url, 'http://localhost').pathname);
    const target = resolve(root, pathname === '/' ? 'index.html' : `.${pathname}`);
    const inside = relative(root, target);
    if (inside.startsWith('..') || isAbsolute(inside)) {
      response.writeHead(403);
      return response.end();
    }
    const content = await readFile(target);
    const extension = target.slice(target.lastIndexOf('.'));
    response.writeHead(200, {
      'Content-Type': `${mime[extension] || 'application/octet-stream'}; charset=utf-8`,
      'Cache-Control': 'no-store',
    });
    response.end(request.method === 'HEAD' ? undefined : content);
  } catch {
    response.writeHead(404);
    response.end('Not found');
  }
});
server.listen(Number(process.env.PORT || 5173), '127.0.0.1', () => {
  console.log(`Kinship Admin: http://127.0.0.1:${server.address().port}`);
});
