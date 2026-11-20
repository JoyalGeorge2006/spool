import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const app = fileURLToPath(new URL('../', import.meta.url));
const text = (path: string) => readFileSync(app + path, 'utf8');
function sources(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((e) =>
    e.isDirectory() ? sources(`${dir}/${e.name}`) : /\.tsx?$/.test(e.name) && !e.name.endsWith('.test.ts') ? [readFileSync(`${dir}/${e.name}`, 'utf8')] : [],
  );
}

describe('audio-only source boundaries', () => {
  it('has no second video player, quality UI or surface owner', () => {
    ['playback/youtube/videoDeck.ts', 'state/video.ts', 'shell/VideoQuality.tsx', 'components/music/VideoSurface.tsx', 'components/music/MotionArtwork.tsx'].forEach((p) =>
      expect(existsSync(app + p), p).toBe(false),
    );
    expect(text('state/player.ts')).not.toContain('followVideo');
    expect(text('playback/youtube/engine.ts')).not.toContain('async videoStream');
  });
  it('never constructs or mounts a picture element in application sources', () => {
    for (const body of sources(app)) {
      expect(body).not.toMatch(/<video\b/);
      expect(body).not.toMatch(/createElement\(['"]video['"]\)/);
    }
  });
  it('keeps audio selection and disables picture manifests', () => {
    const engine = text('playback/youtube/engine.ts');
    expect(engine).toContain('disableVideo: true');
    expect(engine.match(/format_filter: \(format\) => format.has_video \|\| !format.has_audio/g)).toHaveLength(2);
    expect(engine).toContain('streaming.adaptive_formats.filter((format) => format.has_audio && !format.has_video).map(buildSabrFormat)');
    expect(text('playback/youtube/ShakaPlayerAdapter.ts')).not.toContain('videoFormat: activeVariant.originalVideoId');
  });
  it('does not expose a Canvas extension command or fetch path', () => {
    expect(text('data/spotify/api.ts')).not.toContain('fetchCanvas');
    expect(text('data/apple/motionArt.ts')).not.toContain('useQuery');
    const protocol = fileURLToPath(new URL('../../../shared/src/bridge/protocol.ts', import.meta.url));
    expect(readFileSync(protocol, 'utf8')).not.toContain("'spotify.canvas'");
  });
});
