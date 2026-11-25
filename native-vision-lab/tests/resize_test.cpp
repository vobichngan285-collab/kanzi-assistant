#include "resize_area.h"
#include <cassert>
#include <iostream>
int main(){
    using namespace bluestar;
    for(auto cap:{0u,3145728u,2239488u,1769472u,786432u,442368u}){
        auto p=fit(3072,4096,cap);auto q=fit(4096,3072,cap);
        assert(p.first==q.second&&p.second==q.first);
        assert(p.first>0&&p.second>0&&p.first<=3072&&p.second<=4096);
        assert(!cap||uint64_t(p.first)*p.second<=cap);
    }
    assert(fit(3072,4096,2239488)==std::make_pair(1296u,1728u));
    assert(fit(2,3,100)==std::make_pair(2u,3u));
    unsigned char a[]={0,20,40, 100,120,140, 200,220,240,  100,120,140};
    auto b=resize_area(a,2,2,1,1);
    assert(b[0]==100&&b[1]==120&&b[2]==140);
    unsigned char c[]={0,0,0, 90,90,90, 180,180,180};
    auto r=resize_area(c,3,1,2,1);
    assert(r[0]==30&&r[3]==150);
    auto same=resize_area(a,2,2,2,2);
    assert(std::equal(same.begin(),same.end(),a));
    std::cout<<"resize tests passed\n";
}
