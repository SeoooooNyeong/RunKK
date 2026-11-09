import sharp from 'sharp';
export const profile=await sharp(Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024">
<ellipse cx="490" cy="480" rx="350" ry="390" fill="#49382d"/>
<path d="M580 300 Q870 260 875 560 L920 625 L880 650 Q890 810 710 820 L710 910 L620 910 L620 800 Q490 740 510 540Z" fill="#f5cfbc"/>
<ellipse cx="760" cy="550" rx="65" ry="90" fill="white" stroke="#33271f" stroke-width="12"/>
<ellipse cx="785" cy="550" rx="30" ry="74" fill="#49382d"/>
<path d="M700 470 Q750 430 815 475" fill="none" stroke="#33271f" stroke-width="14"/>
<path d="M700 420 Q760 390 815 420" fill="none" stroke="#49382d" stroke-width="16"/>
<path d="M810 750 L870 740" stroke="#b95c57" stroke-width="8"/>
</svg>`)).png().toBuffer();
