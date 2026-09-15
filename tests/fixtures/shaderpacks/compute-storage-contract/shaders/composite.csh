#version 430

layout(local_size_x = 1, local_size_y = 1, local_size_z = 1) in;
const ivec3 workGroups = ivec3(1, 1, 1);

layout(std430, binding = 0) buffer Phase15Buffer {
    uvec4 phase15Data;
};
layout(rgba8, binding = 1) uniform writeonly image2D phase15Image;

void main() {
    phase15Data = uvec4(0x50483135u, 0x0000A11Cu, 0u, 1u);
    imageStore(phase15Image, ivec2(0), vec4(1.0, 0.0, 0.0, 1.0));
}
