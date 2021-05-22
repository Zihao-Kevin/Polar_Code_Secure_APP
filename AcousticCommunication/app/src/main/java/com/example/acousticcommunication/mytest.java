package com.example.acousticcommunication;


import android.provider.Settings;

class mytest {
    public static void main(String[] args)
    {
        Math_main MM = new Math_main();
        MM.cal();

        MM.get_u();
        System.out.println(MM.u);

       // MM.get_y();

        MM.get_Error();
        System.out.println(MM.error_bob);
        System.out.println(MM.error_eve);
    }

}
