#pragma once
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <vector>
#include <utility>
namespace bluestar {
inline std::pair<uint32_t,uint32_t> fit(uint32_t w,uint32_t h,uint32_t cap) {
    if (!cap || uint64_t(w)*h<=cap) return {w,h};
    const double s=std::sqrt(double(cap)/(double(w)*h));
    auto a=std::max(1u,uint32_t(w*s)),b=std::max(1u,uint32_t(h*s));
    if(uint64_t(a)*b>cap){if(a>=b)a=cap/b;else b=cap/a;}
    return {a,b};
}
// Exact area-weighted RGB box downsampling, no JPEG/PNG re-encoding.
inline std::vector<unsigned char> resize_area(const unsigned char* src,uint32_t w,uint32_t h,uint32_t dw,uint32_t dh) {
    std::vector<unsigned char> dst(size_t(dw)*dh*3);
    const uint64_t denom=uint64_t(w)*h;
    for(uint32_t y=0;y<dh;y++){
        const uint64_t top=uint64_t(y)*h,bottom=uint64_t(y+1)*h;
        for(uint32_t x=0;x<dw;x++){
            const uint64_t left=uint64_t(x)*w,right=uint64_t(x+1)*w;
            uint64_t sums[3]={0,0,0};
            for(uint32_t sy=top/dh;sy<(bottom+dh-1)/dh;sy++){
                const uint64_t wy=std::min(bottom,uint64_t(sy+1)*dh)-std::max(top,uint64_t(sy)*dh);
                for(uint32_t sx=left/dw;sx<(right+dw-1)/dw;sx++){
                    const uint64_t wx=std::min(right,uint64_t(sx+1)*dw)-std::max(left,uint64_t(sx)*dw);
                    const uint64_t k=wx*wy;
                    const auto p=src+(size_t(sy)*w+sx)*3;
                    for(int c=0;c<3;c++)sums[c]+=k*p[c];
                }
            }
            for(int c=0;c<3;c++)dst[(size_t(y)*dw+x)*3+c]=(sums[c]+denom/2)/denom;
        }
    }
    return dst;
}
}
