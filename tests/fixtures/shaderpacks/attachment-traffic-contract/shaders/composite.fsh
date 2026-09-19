#version 120

/* DRAWBUFFERS:0 */

// The elidable writer. Every pixel of colortex0 is written from the fragment's own coordinates and nothing
// here samples a target, so whatever stood in colortex0 cannot reach the picture and the load of it is the
// traffic `-Dvitrail.elideTargetTraffic` claims to remove.
//
// The pattern is a 16-pixel checker in fragment space - no world, no sun, no pack clock - so two launches of
// this fixture draw the same frame, and a difference between them is the switch rather than the weather.
void main() {
    float checker = mod(floor(gl_FragCoord.x / 16.0) + floor(gl_FragCoord.y / 16.0), 2.0);
    gl_FragData[0] = vec4(checker, 1.0 - checker, 0.5, 1.0);
}
