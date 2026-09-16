#version 430

layout(local_size_x = 1, local_size_y = 1, local_size_z = 1) in;
const ivec3 workGroups = ivec3(1, 1, 1);

layout(std430, binding = 0) buffer Phase15Buffer {
    uvec4 phase15Data;
};
layout(rgba8, binding = 1) uniform image2D phase15Image;

void main() {
    vec4 previous = imageLoad(phase15Image, ivec2(0));
    bool imageOk = previous.r > 0.90 && previous.g < 0.10 && previous.b < 0.10;
    bool bufferOk = phase15Data.x == 0x50483135u
            && phase15Data.y == 0x0000A11Cu
            && phase15Data.w == 1u;
    bool ok = imageOk && bufferOk;

    phase15Data = ok
            ? uvec4(0x50483135u, 0x0000A11Cu, 0x0000BEEFu, 2u)
            : uvec4(0x50483135u, 0x0000DEADu, 0x0000DEADu, 2u);
    imageStore(phase15Image, ivec2(0),
            ok ? vec4(0.0, 1.0, 0.0, 1.0) : vec4(1.0, 0.0, 1.0, 1.0));
}
