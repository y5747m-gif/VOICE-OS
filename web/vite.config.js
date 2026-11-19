import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import { createVoiceApi } from './server/voice.mjs';
import { createGroqApi } from './server/groq.mjs';
import { createAndroidReleaseApi } from './server/android-release.mjs';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  return {
    plugins: [react(), {
      name: 'voice-os-server-voice',
      configureServer(server) {
        server.middlewares.use(createGroqApi({
          apiKey: process.env.GROQ_API_KEY ?? env.GROQ_API_KEY,
          transcriptionModel: process.env.GROQ_TRANSCRIPTION_MODEL ?? env.GROQ_TRANSCRIPTION_MODEL,
          commandModel: process.env.GROQ_COMMAND_MODEL ?? env.GROQ_COMMAND_MODEL,
        }));
        server.middlewares.use(createVoiceApi({ apiKey: process.env.ELEVENLABS_API_KEY ?? env.ELEVENLABS_API_KEY }));
        server.middlewares.use(createAndroidReleaseApi({
          directory: process.env.ANDROID_RELEASE_DIR ?? env.ANDROID_RELEASE_DIR,
          signer: process.env.ANDROID_SIGNER_SHA256 ?? env.ANDROID_SIGNER_SHA256,
        }));
      },
    }],
    envPrefix: 'VOICE_PUBLIC_',
    server: { host: '0.0.0.0', port: 5173, strictPort: true, allowedHosts: ['.e2b.app'] },
  };
});
