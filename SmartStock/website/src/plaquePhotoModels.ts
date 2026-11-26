// Pixel-space front silhouettes traced from the actual variant photos (900 × 1200).
// Keeping the photograph's UVs preserves the real faceting, blue insert, metal fittings,
// and bases. Extruded side depth is illustrative; these are not manufacturing meshes.
export type PhotoPart={path:string;depth:number;edge:string};
export type PlaquePhotoModel={bounds:[number,number,number,number];printBounds:[number,number,number,number];parts:PhotoPart[]};
export const plaquePhotoModels:Record<string,PlaquePhotoModel>={
 diamond:{bounds:[105,130,810,925],printBounds:[110,135,810,803],parts:[
 {path:'M448 133 Q465 136 484 157 L780 463 Q816 499 806 535 Q803 552 780 576 L517 789 Q452 829 391 781 L134 564 Q96 530 111 490 Q115 474 139 450 L406 162 Q428 135 448 133 Z',depth:.06,edge:'#9baeb4'},
 {path:'M276 772 L646 770 L673 838 L676 876 L661 907 L253 923 L246 860 L249 835 Z',depth:.23,edge:'#51331e'},
 {path:'M303 772 L623 769 L634 816 L292 826 Z',depth:.1,edge:'#a3acb4'}]},
 star:{bounds:[233,95,597,1045],printBounds:[236,99,596,891],parts:[
 {path:'M410 98 L476 257 L596 251 L520 887 L392 891 L322 457 L350 330 L238 260 L373 260 Z',depth:.045,edge:'#adbec0'},
 {path:'M354 846 L554 844 L590 1040 L321 1043 Z',depth:.22,edge:'#151b20'}]},
 flame:{bounds:[142,26,599,1105],printBounds:[144,225,572,979],parts:[
 {path:'M490 194 Q574 390 573 595 Q567 797 466 960 L310 979 L143 563 L389 284 Z',depth:.055,edge:'#9eafb5'},
 {path:'M434 27 L471 147 L564 143 L488 217 L525 378 L431 263 L353 302 L377 207 L316 144 L406 150 Z',depth:.035,edge:'#c9ac21'},
 {path:'M190 961 L542 913 L598 1041 L575 1064 L181 1104 L175 1077 Z',depth:.22,edge:'#a9b4b4'}]},
 pentagon:{bounds:[249,215,787,1098],printBounds:[254,217,758,998],parts:[
 {path:'M415 218 L724 559 L758 758 L644 953 L405 995 L253 739 Z',depth:.055,edge:'#9aaeb3'},
 {path:'M325 931 L731 890 L786 1047 L773 1084 L272 1096 L294 975 Z',depth:.2,edge:'#b1c1c2'}]},
 heart:{bounds:[122,316,761,963],printBounds:[125,316,758,891],parts:[
 {path:'M444 368 C362 362 332 322 267 337 C170 354 111 432 127 527 C146 677 302 781 461 891 C596 803 730 658 753 532 C781 410 682 298 571 321 C522 328 493 367 444 368 Z',depth:.065,edge:'#a4b8ba'},
 {path:'M239 844 L448 791 L663 816 L694 933 L226 962 Z',depth:.15,edge:'#a9bfc2'}]},
 pointed:{bounds:[120,170,728,987],printBounds:[123,173,717,887],parts:[
 {path:'M122 175 Q438 139 715 417 L626 861 L383 883 Z',depth:.045,edge:'#afc0c2'},
 {path:'M302 877 L690 836 L728 954 L715 975 L280 988 Z',depth:.17,edge:'#a8bbc0'},
 {path:'M434 887 C432 801 571 794 576 881 L576 913 L433 922 Z',depth:.065,edge:'#a9a9ab'}]},
 curved:{bounds:[152,188,776,1093],printBounds:[154,187,774,979],parts:[
 {path:'M155 290 C156 192 262 170 330 202 L774 448 L645 950 L368 979 Z',depth:.045,edge:'#a5b5b9'},
 {path:'M312 973 L699 923 L754 1053 L741 1077 L303 1091 Z',depth:.17,edge:'#a6b5b7'},
 {path:'M436 993 C431 898 565 894 580 981 L578 1025 L438 1037 Z',depth:.065,edge:'#a4a4a5'}]},
 rounded:{bounds:[65,176,806,1026],printBounds:[70,179,801,1018],parts:[
 {path:'M401 183 Q441 168 481 193 L753 329 Q809 353 800 422 Q780 757 626 971 Q610 995 571 1003 L347 1018 Q314 1025 282 996 Q119 770 72 466 Q58 432 84 395 L352 212 Z',depth:.045,edge:'#9eafb0'},
 {path:'M464 952 Q486 945 495 970 L495 1020 L467 1025 Z',depth:.07,edge:'#a1a9ae'}]},
 shield:{bounds:[130,106,762,907],printBounds:[133,108,761,901],parts:[
 {path:'M447 108 L652 217 Q662 294 760 326 Q742 642 659 889 L251 901 Q161 611 132 331 Q223 321 250 217 Z',depth:.045,edge:'#a5b3b6'},
 {path:'M448 856 Q456 842 466 855 L476 879 L454 892 L439 879 Z',depth:.065,edge:'#a7acb1'}]}
};
