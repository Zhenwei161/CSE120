#include "stdio.h"
#include "stdlib.h"

int main()
{
  int status = -1;

  int id2 = exec("swap4.coff", 0, null);

  int id3 = exec("swap4.coff", 0, null);
  int id4 = exec("swap4.coff", 0, null);
//  int id5 = exec("swap5.coff", 0, null);
/*  int id6 = exec("swap5.coff", 0, null);
  int id7 = exec("swap5.coff", 0, null);
  int id8 = exec("swap5.coff", 0, null);
  int id9 = exec("swap5.coff", 0, null); */
  join(id2, &status);
  printf("First swap status return is: %d\n", status);
  join(id3, &status);
  printf("Second swap status return is: %d\n", status);
  join(id4, &status);
  printf("3rd swap status return is: %d\n", status);
/*  join(id5, &status);
  printf("4th swap status return is: %d\n", status);
  join(id6, &status);
  printf("5 swap status return is: %d\n", status);
  join(id7, &status);
  printf("6 swap status return is: %d\n", status);
  join(id8, &status);
  printf("7 swap status return is: %d\n", status);
  join(id9, &status);
  printf("8 swap status return is: %d\n", status);*/

  exit(0);
}
