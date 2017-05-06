#pragma version(1)
#pragma rs java_package_name(com.example.shu.renderscriptvideo)


rs_allocation gIn;


uchar4 RS_KERNEL yuvToRgb_greyscale(uint32_t x, uint32_t y) {
     uchar yp = rsGetElementAtYuv_uchar_Y(gIn, x, y) & 0xFF;

    uchar4 res4;
    res4.r = yp;
    res4.g = yp;
    res4.b = yp;
    res4.a = 0xFF;

    return res4;
}